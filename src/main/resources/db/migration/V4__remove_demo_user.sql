DELETE FROM refresh_tokens
WHERE user_id = '3746e32f-1bd6-4023-a608-f535696f5e01';

DELETE FROM users
WHERE id = '3746e32f-1bd6-4023-a608-f535696f5e01'
  AND email = 'user-dummy@example.com';
