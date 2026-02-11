-- Token Bucket Rate Limiter (Lua script for Redis)
--
-- This script runs atomically in Redis — no race conditions between
-- concurrent gateway instances checking the same client's bucket.
--
-- KEYS[1] = rate limit key (e.g., "ratelimit:client123:mock-backend")
-- ARGV[1] = max tokens (burst size)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current timestamp in milliseconds
--
-- Returns:
--   >= 0: number of remaining tokens (request allowed)
--   -1:   bucket exhausted (request denied)

local key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Get current bucket state
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Initialize bucket if it doesn't exist
if tokens == nil then
    tokens = max_tokens
    last_refill = now
end

-- Calculate tokens to add since last refill
local elapsed_ms = now - last_refill
local elapsed_seconds = elapsed_ms / 1000.0
local new_tokens = elapsed_seconds * refill_rate

-- Refill the bucket (capped at max)
tokens = math.min(max_tokens, tokens + new_tokens)

-- Try to consume one token
if tokens < 1 then
    -- Bucket empty — deny request
    redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 60) -- TTL to prevent orphan keys
    return -1
end

-- Consume token and update state
tokens = tokens - 1
redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
redis.call('EXPIRE', key, 60) -- Refresh TTL

return math.floor(tokens)
