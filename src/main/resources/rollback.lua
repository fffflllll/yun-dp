-- KEYS[1]: 库存 Key (seckill:stock:1001)
-- KEYS[2]: 订单 Set Key (seckill:order:1001)
-- ARGV[1]: 用户 ID

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userId = ARGV[1]

-- 1. 尝试从 Set 中移除用户
-- srem 命令会返回被移除的元素数量：
-- 如果返回 1，说明用户刚才在里面，现在被移除了 -> 需要恢复库存
-- 如果返回 0，说明用户不在里面（可能已经回滚过了） -> 不需要恢复库存
local result = redis.call('srem', orderKey, userId)

if (result == 1) then
    -- 2. 只有移除成功，才恢复库存
    redis.call('incrby', stockKey, 1)
    return 1 -- 回滚成功
end

return 0 -- 无需回滚（重复回滚）