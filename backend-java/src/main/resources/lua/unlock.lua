-- 分布式锁释放（比较后删除）：KEYS[1]=lock key，ARGV[1]=持有者标识
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
