// RedisConfig.java
// package com.financescope.financescope.config;

// import com.fasterxml.jackson.annotation.JsonTypeInfo;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.type.TypeFactory;
// import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.data.redis.connection.RedisConnectionFactory;
// import org.springframework.data.redis.core.RedisTemplate;
// import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
// import org.springframework.data.redis.serializer.StringRedisSerializer;

// @Configuration
// public class RedisConfig {

//     @Bean
//     public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
//         RedisTemplate<String, Object> template = new RedisTemplate<>();
//         template.setConnectionFactory(connectionFactory);

//         // Key는 String으로 직렬화
//         template.setKeySerializer(new StringRedisSerializer());
//         template.setHashKeySerializer(new StringRedisSerializer());

//         // Value는 JSON으로 직렬화
//         ObjectMapper objectMapper = new ObjectMapper();
//         objectMapper.registerModule(new JavaTimeModule());
//         objectMapper.activateDefaultTyping(
//                 TypeFactory.defaultInstance(),
//                 ObjectMapper.DefaultTyping.NON_FINAL,
//                 JsonTypeInfo.As.PROPERTY
//         );

//         GenericJackson2JsonRedisSerializer jsonSerializer = 
//                 new GenericJackson2JsonRedisSerializer(objectMapper);

//         template.setValueSerializer(jsonSerializer);
//         template.setHashValueSerializer(jsonSerializer);

//         template.afterPropertiesSet();
//         return template;
//     }

//     @Bean
//     public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
//         RedisTemplate<String, String> template = new RedisTemplate<>();
//         template.setConnectionFactory(connectionFactory);
        
//         // 모든 직렬화를 String으로 설정
//         StringRedisSerializer stringSerializer = new StringRedisSerializer();
//         template.setKeySerializer(stringSerializer);
//         template.setValueSerializer(stringSerializer);
//         template.setHashKeySerializer(stringSerializer);
//         template.setHashValueSerializer(stringSerializer);
        
//         template.afterPropertiesSet();
//         return template;
//     }
// }
