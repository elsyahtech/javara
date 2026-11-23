const AUTH_PATH = "/api/v1/auth";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

export class AuthRefreshError extends Error {
  constructor(response) {
    super(`JAVARA token refresh failed with HTTP ${response.status}`);
    this.name = "AuthRefreshError";
    this.response = response;
  }
}

export class JavaraAuthClient {
  #baseUrl;
  #fetch;
  #csrf;
  #csrfPromise;
  #refreshPromise;

  constructor({ baseUrl, fetchImpl = globalThis.fetch }) {
    if (!baseUrl) {
      throw new TypeError("baseUrl is required");
    }
    if (typeof fetchImpl !== "function") {
      throw new TypeError("fetchImpl must be a function");
    }
    this.#baseUrl = new URL(baseUrl);
    this.#fetch = fetchImpl;
  }

  async request(input, init = {}) {
    const request = await this.#createRequest(input, init);
    const firstResponse = await this.#send(request);
    if (firstResponse.status !== 401 || this.#isAuthEndpoint(request.url)) {
      return firstResponse;
    }

    const refreshed = await this.#refreshOnce();
    if (!refreshed) {
      return firstResponse;
    }
    return this.#send(request);
  }

  async login(credentials) {
    return this.request(`${AUTH_PATH}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials)
    });
  }

  async logout() {
    return this.request(`${AUTH_PATH}/logout`, { method: "POST" });
  }

  #createRequest(input, init) {
    const url = input instanceof Request
      ? new URL(input.url)
      : new URL(input, this.#baseUrl);
    if (url.origin !== this.#baseUrl.origin) {
      throw new TypeError("JAVARA requests must use the configured API origin");
    }
    const request = new Request(url, { ...init, credentials: "include" });
    const method = request.method.toUpperCase();
    if (SAFE_METHODS.has(method)) {
      return request;
    }
    return this.#withCsrf(request);
  }

  async #withCsrf(request) {
    const csrf = await this.#getCsrf();
    const headers = new Headers(request.headers);
    headers.set(csrf.headerName, csrf.token);
    return new Request(request, { headers });
  }

  async #send(request) {
    return this.#fetch(request.clone());
  }

  async #getCsrf() {
    if (this.#csrf) {
      return this.#csrf;
    }
    if (!this.#csrfPromise) {
      this.#csrfPromise = this.#requestCsrf();
    }
    try {
      return await this.#csrfPromise;
    } finally {
      this.#csrfPromise = undefined;
    }
  }

  async #requestCsrf() {
    const response = await this.#fetch(new Request(
      new URL(`${AUTH_PATH}/csrf`, this.#baseUrl),
      { credentials: "include" }
    ));
    if (!response.ok) {
      throw new Error(`JAVARA CSRF request failed with HTTP ${response.status}`);
    }
    const body = await response.json();
    const token = body?.data?.token;
    const headerName = body?.data?.headerName;
    if (typeof token !== "string" || typeof headerName !== "string") {
      throw new TypeError("JAVARA returned an invalid CSRF token response");
    }
    this.#csrf = { token, headerName };
    return this.#csrf;
  }

  async #refreshOnce() {
    if (this.#refreshPromise) {
      return this.#refreshPromise;
    }
    this.#refreshPromise = this.#withRefreshLock(() => this.#refresh());
    try {
      return await this.#refreshPromise;
    } finally {
      this.#refreshPromise = undefined;
    }
  }

  async #withRefreshLock(operation) {
    const locks = globalThis.navigator?.locks;
    if (locks?.request) {
      return locks.request("javara-auth-refresh", operation);
    }
    return operation();
  }

  async #refresh() {
    const csrf = await this.#getCsrf();
    const response = await this.#fetch(new Request(
      new URL(`${AUTH_PATH}/refresh`, this.#baseUrl),
      {
        method: "POST",
        credentials: "include",
        headers: { [csrf.headerName]: csrf.token }
      }
    ));
    if (response.status === 401) {
      return false;
    }
    if (!response.ok) {
      throw new AuthRefreshError(response);
    }
    return true;
  }

  #isAuthEndpoint(url) {
    const path = new URL(url).pathname;
    return path === AUTH_PATH || path.startsWith(`${AUTH_PATH}/`);
  }
}
