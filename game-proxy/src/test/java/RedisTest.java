//import com.game.AppApplication;
//import com.game.redis.RedisService;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.redis.core.RedisTemplate;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.TimeUnit;
//
//@SpringBootTest(classes = {AppApplication.class})
//public class RedisTest {
//
//    /**
//     * redis序列化的工具配置类，下面这个请一定开启配置
//     * 127.0.0.1:6379 keys *
//     * 1)"ord:102" 序列化过
//     * 2)"\xac\xed\x00\x05t\ord:102"  没有序列化过
//     * this.redisTemplate.opsForValue()//提供了操作String类型的所有方法
//     * this.redisTemplate.opsForList()//提供了操作List类型的所有方法
//     * this.redisTemplate.opsForSet()//提供了操作Set类型的所有方法
//     * this.redisTemplate.opsForHash()//提供了操作Hash类型的所有方法
//     * this.redisTemplate.opsForZSet()//提供了操作ZSet类型的所有方法
//     *
//     *
//     * @param lettuceConnectionFactory
//     * @return
//     */
//
//    @Autowired
//    RedisTemplate redisTemplate;
//
//    @Autowired
//    private RedisService redisService;
//
//    @Test
//    public void test1(){
//
//        //boundhashOps也是操作redis的一种方法，但是是低版本操作。
//        //操作方式和opsForhash一样。根据个人喜好都可以
////        Object content = redisTemplate.boundHashOps("content").get("1");
//        Long content = redisService.getCacheObject("lottery_open_time_ylbwz");
//        log.info(content);
//    }
//
//    /**
//     存储string类型
//     */
//    @Test
//    public void test2(){
//        /**
//         *  redisTemplate.opsForValue()//提供了操作String类型的所有方法
//         */
//        Object name ="dession";
//        redisService.setCacheObject("tt", "测试的");
//        String tt = redisService.getCacheObject("tt");
//        log.info(tt);
//
//
//        redisTemplate.opsForValue().set("name",name);
//        //设置过期时间1天过期
//        redisTemplate.expire("name",1, TimeUnit.DAYS);
//
//        log.info("redis:" + redisTemplate.opsForValue().get("name"));
//        //  结果为：dession
//
//
//
//    }
//
//    /**
//     存储Hash数据类型
//     */
//    @Test
//    public void test3(){
//        //单个存储
//        redisTemplate.opsForHash().put("users","name","zhangsan");
//        //设置过期时间
//        redisTemplate.expire("users",1, TimeUnit.DAYS);
//        //多只存储
//        Map map = new HashMap();
//        map.put("age",12);
//        map.put("sex","男");
//        redisTemplate.opsForHash().putAll("users",map);
//
//        Object o = redisTemplate.opsForHash().get("users", "name");
//        log.info(o);
//
//
//
//    }
//
//}
