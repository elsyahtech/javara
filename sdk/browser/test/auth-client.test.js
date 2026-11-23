import assert from "node:assert/strict";
import test from "node:test";

import { JavaraAuthClient } from "../src/auth-client.js";

const csrfResponse = () => new Response(JSON.stringify({
  data: { token: "csrf-value", headerName: "X-XSRF-TOKEN" }
}), { status: 200, headers: { "Content-Type": "application/json" } });

test("refreshes once and retries a protected request with cookies", async () => {
  const calls = [];
  let protectedRequests = 0;
  const client = new JavaraAuthClient({
    baseUrl: "https://api.example.test",
    fetchImpl: async (request) => {
      calls.push(request);
      if (request.url.endsWith("/csrf")) return csrfResponse();
      if (request.url.endsWith("/refresh")) return new Response(null, { status: 200 });
      protectedRequests += 1;
      return new Response(null, { status: protectedRequests === 1 ? 401 : 200 });
    }
  });

  const response = await client.request("/api/v1/users");

  assert.equal(response.status, 200);
  assert.equal(protectedRequests, 2);
  assert.equal(calls.filter((request) => request.url.endsWith("/refresh")).length, 1);
  assert.ok(calls.every((request) => request.credentials === "include"));
});

test("coalesces concurrent unauthorized requests into one refresh", async () => {
  let refreshRequests = 0;
  let protectedRequests = 0;
  const client = new JavaraAuthClient({
    baseUrl: "https://api.example.test",
    fetchImpl: async (request) => {
      if (request.url.endsWith("/csrf")) return csrfResponse();
      if (request.url.endsWith("/refresh")) {
        refreshRequests += 1;
        await new Promise((resolve) => setTimeout(resolve, 5));
        return new Response(null, { status: 200 });
      }
      protectedRequests += 1;
      return new Response(null, { status: protectedRequests <= 2 ? 401 : 200 });
    }
  });

  const [first, second] = await Promise.all([
    client.request("/api/v1/users", { headers: { "X-Retry": "yes" } }),
    client.request("/api/v1/users", { headers: { "X-Retry": "yes" } })
  ]);

  assert.equal(first.status, 200);
  assert.equal(second.status, 200);
  assert.equal(refreshRequests, 1);
});

test("does not refresh an unauthorized auth endpoint", async () => {
  let refreshRequests = 0;
  let loginRequest;
  const client = new JavaraAuthClient({
    baseUrl: "https://api.example.test",
    fetchImpl: async (request) => {
      if (request.url.endsWith("/csrf")) return csrfResponse();
      if (request.url.endsWith("/refresh")) refreshRequests += 1;
      if (request.url.endsWith("/login")) loginRequest = request;
      return new Response(null, { status: 401 });
    }
  });

  const response = await client.login({ email: "user@example.test", password: "example" });

  assert.equal(response.status, 401);
  assert.equal(refreshRequests, 0);
  assert.equal(loginRequest.headers.get("X-XSRF-TOKEN"), "csrf-value");
  assert.equal(loginRequest.credentials, "include");
});

test("refuses to send API cookies to a different origin", async () => {
  const client = new JavaraAuthClient({
    baseUrl: "https://api.example.test",
    fetchImpl: async () => {
      throw new Error("fetch should not be called");
    }
  });

  await assert.rejects(
    client.request("https://attacker.example.test/collect"),
    /configured API origin/
  );
});
