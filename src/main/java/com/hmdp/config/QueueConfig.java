package com.hmdp.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
@Configuration
public class QueueConfig {


    //普通交换机名称
    public static final String X_EXCHANGE = "X";
    //死信交换机名称
    public static final String Y_DEAD_LETTER_EXCHANGE = "Y";
    //普通队列名称
    public static final String QUEUE_A = "QA";
    //死信队列名称
    public static final String DEAD_LETTER_QUEUE_D = "QD";
    public static final String MEET_PLAN_EXCHANGE = "meet.plan.exchange";
    // v1 队列在旧环境中没有死信参数，RabbitMQ 不允许原地修改队列参数。
    public static final String MEET_PLAN_QUEUE = "meet.plan.execute.v2";
    public static final String MEET_PLAN_ROUTING_KEY = "meet.plan.execute";
    public static final String MEET_PLAN_DEAD_LETTER_EXCHANGE =
            "meet.plan.dead-letter.exchange";
    public static final String MEET_PLAN_DEAD_LETTER_QUEUE =
            "meet.plan.dead-letter";
    public static final String MEET_PLAN_DEAD_LETTER_ROUTING_KEY =
            "meet.plan.dead-letter";


    /**
     * 声明x交换机
     * @return
     */
    @Bean("xExchange")//别名和方法名取一样
    public DirectExchange xExchange(){
        return new DirectExchange(X_EXCHANGE);
    }
    /**
     * 声明y交换机 死信交换机
     * @return
     */
    @Bean("yExchange")//别名和方法名取一样
    public DirectExchange yExchange(){
        return new DirectExchange(Y_DEAD_LETTER_EXCHANGE);
    }

    /**
     * 声明队列A
     * @return
     */
    @Bean("queueA")
    public Queue queueA(){
        final HashMap<String, Object> arguments
                = new HashMap<>();
        //设置死信交换机
        arguments.put("x-dead-letter-exchange",Y_DEAD_LETTER_EXCHANGE);
        //设置死信RoutingKey
        arguments.put("x-dead-letter-routing-key","YD");

        return QueueBuilder.durable(QUEUE_A)
                .withArguments(arguments)
                .deadLetterExchange(Y_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("YD")
                .build();
    }

    /**
     * 声明死信队列D
     * @return
     */
    @Bean("queueD")
    public Queue queueD(){
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_D)
                .build();
    }

    /**
     * A队列绑定X交换机
     * @param queueA
     * @return
     */
    @Bean
    public Binding queueABindingX(@Qualifier("queueA")Queue queueA,
                                  @Qualifier("xExchange") DirectExchange xExchange){

        return BindingBuilder.bind(queueA).to(xExchange).with("XA");
    }

    /**
     * d队列绑定y交换机
     * @param queueD
     * @return
     */
    @Bean
    public  Binding queueDBindingY(@Qualifier("queueD")Queue queueD,
                                   @Qualifier("yExchange") DirectExchange yExchange
    ){
        return BindingBuilder.bind(queueD).to(yExchange).with("YD");
    }

    @Bean
    public DirectExchange meetPlanExchange() {
        return new DirectExchange(MEET_PLAN_EXCHANGE, true, false);
    }

    @Bean
    public Queue meetPlanQueue() {
        return QueueBuilder.durable(MEET_PLAN_QUEUE)
                .deadLetterExchange(MEET_PLAN_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MEET_PLAN_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding meetPlanBinding(
            @Qualifier("meetPlanQueue") Queue queue,
            @Qualifier("meetPlanExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MEET_PLAN_ROUTING_KEY);
    }

    @Bean
    public DirectExchange meetPlanDeadLetterExchange() {
        return new DirectExchange(
                MEET_PLAN_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue meetPlanDeadLetterQueue() {
        return QueueBuilder.durable(MEET_PLAN_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding meetPlanDeadLetterBinding(
            @Qualifier("meetPlanDeadLetterQueue") Queue queue,
            @Qualifier("meetPlanDeadLetterExchange")
            DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MEET_PLAN_DEAD_LETTER_ROUTING_KEY);
    }
    /**
     * 消息转换器
     * @return
     */
    @Bean
    public MessageConverter messageConverter(){
        // Spring Boot 4 默认使用 Jackson 3；避免引入仅为消息转换器服务的 Jackson 2。
        return new JacksonJsonMessageConverter();
    }


}
