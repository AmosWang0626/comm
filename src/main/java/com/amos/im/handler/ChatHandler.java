package com.amos.im.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.amos.im.common.GeneralCode;
import com.amos.im.common.GeneralResponse;
import com.amos.im.core.command.request.MessageRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * DESCRIPTION: 测试 Handler
 *
 * @author <a href="mailto:daoyuan0626@gmail.com">amos.wang</a>
 * @date 2020/7/8
 */
@Component
public class ChatHandler implements WebSocketHandler {

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        Map<String, String> queryMap = getUrlParams(query);
        String token = queryMap.getOrDefault("token", "");

        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    MessageRequest messageRequest = JSONObject.parseObject(payload, MessageRequest.class);
                    String receiverToken = messageRequest.getReceiver();

                    return Optional.ofNullable(WebsocketSenderRegistry.get(receiverToken))
                            .map(WebSocketSender::getSession)
                            .map(toSession -> toSession.send(Mono.just(toSession.textMessage(payload))))
                            // MonoIgnoreThen外部访问不到 故此处使用名字比较
                            .filter(mono -> !"MonoIgnoreThen".equals(mono.toString()))
                            .orElseGet(() -> offline(session, receiverToken));
                })
                .then();

        // 保存用户在线状态
        Mono<Void> output = session.send(Flux.create(sink ->
                WebsocketSenderRegistry.put(token, new WebSocketSender(session, sink))));

        return Mono.zip(input, output).then();
    }

    /**
     * 用户不在线
     */
    private Mono<Void> offline(WebSocketSession session, String token) {
        if (WebsocketSenderRegistry.containsKey(token)) {
            WebsocketSenderRegistry.remove(token);
        }

        return session.send(Mono.just(session.textMessage(
                JSON.toJSONString(new GeneralResponse<>(GeneralCode.FAIL, "目标用户不在线")))));
    }


    /**
     * 根据 URL后置参数获取 对应参数 Map
     *
     * @param params URL后置参数
     * @return 参数 Map
     */
    private Map<String, String> getUrlParams(String params) {
        if (StringUtils.isBlank(params)) {
            return Collections.emptyMap();
        }

        String[] queryParam = params.split("&");
        Map<String, String> queryMap = new HashMap<>(queryParam.length);
        Arrays.stream(queryParam).forEach(s -> {
            String[] kv = s.split("=", 2);
            String value = kv.length == 2 ? kv[1] : "";
            queryMap.put(kv[0], value);
        });

        return queryMap;
    }

}
