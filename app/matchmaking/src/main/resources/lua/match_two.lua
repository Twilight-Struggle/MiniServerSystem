-- どこで: Matchmaking Redis Lua スクリプト
-- 何を: queue から2件を選び ticket 状態を MATCHED へ更新する原子処理を行う
-- なぜ: pop/update/remove の分離による競合不整合を防ぐため
--
-- 想定 I/F:
-- KEYS[1] = mm:queue:{mode}
-- ARGV[1] = matched_at epoch millis
-- ARGV[2] = generated match_id
--
-- 戻り値:
-- 成立時: {"matched", ticket_id_1, ticket_id_2, match_id}
-- 不成立時: {"no_match"}
local queue_key = KEYS[1]
local now_millis = tonumber(ARGV[1])
local match_id = ARGV[2]

local max_scan = 20
local valid = {}
local scores = {}

for i = 1, max_scan do
  if #valid >= 2 then
    break
  end

  local popped = redis.call("ZPOPMIN", queue_key, 1)
  if popped == nil or #popped == 0 then
    break
  end

  local ticket_id = popped[1]
  local score = tonumber(popped[2])
  local ticket_key = "mm:ticket:" .. ticket_id

  local status = redis.call("HGET", ticket_key, "status")
  local expires_at_millis_raw = redis.call("HGET", ticket_key, "expires_at_epoch_millis")
  local expires_at_millis = tonumber(expires_at_millis_raw)

  if status == "QUEUED" and expires_at_millis ~= nil and expires_at_millis > now_millis then
    table.insert(valid, ticket_id)
    table.insert(scores, score)
  end
end

if #valid < 2 then
  for i = 1, #valid do
    redis.call("ZADD", queue_key, scores[i], valid[i])
  end
  return {"no_match"}
end

for i = 1, 2 do
  local ticket_key = "mm:ticket:" .. valid[i]
  redis.call("HSET", ticket_key, "status", "MATCHED")
  redis.call("HSET", ticket_key, "match_id", match_id)
end

return {"matched", valid[1], valid[2], match_id}
