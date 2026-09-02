-- 原子扣减库存：KEYS[1]=stock key
-- 返回：-2=未预热；-1=已无库存；>=0=扣减后的剩余库存
local stock = redis.call('GET', KEYS[1])
if not stock then
    return -2
end
stock = tonumber(stock)
if stock <= 0 then
    return -1
end
redis.call('DECR', KEYS[1])
return stock - 1
