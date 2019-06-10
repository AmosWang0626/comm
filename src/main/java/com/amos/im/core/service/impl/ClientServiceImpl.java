package com.amos.im.core.service.impl;

import com.amos.im.common.protocol.PacketCodec;
import com.amos.im.common.protocol.PacketSplitter;
import com.amos.im.common.util.LogUtils;
import com.amos.im.common.util.RedisUtil;
import com.amos.im.core.config.ImConfig;
import com.amos.im.core.constant.RedisKeys;
import com.amos.im.core.handler.*;
import com.amos.im.core.command.request.LoginRequest;
import com.amos.im.core.service.ClientService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * PROJECT: Sales
 * DESCRIPTION: 客户端核心实现
 *
 * @author amos
 * @date 2019/6/2
 */
@Service("clientService")
public class ClientServiceImpl implements ClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientServiceImpl.class);

    @Resource
    private ImConfig imConfig;

    private volatile static NioEventLoopGroup WORKER_GROUP;

    /**
     * 连接失败, 最大重试次数
     */
    private static final int MAX_RETRY = 3;


    @Override
    public void start(LoginRequest loginRequest) {
        initGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                .group(WORKER_GROUP)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new PacketSplitter());
                        ch.pipeline().addLast(PacketCodec.INSTANCE);
                        ch.pipeline().addLast(new LoginResponseHandler());
                        ch.pipeline().addLast(new AuthHandler());
                        ch.pipeline().addLast(new MessageResponseHandler());
                        ch.pipeline().addLast(new GroupCreateResponseHandler());
                        ch.pipeline().addLast(new GroupJoinResponseHandler());
                        ch.pipeline().addLast(new GroupMemberListResponseHandler());
                        ch.pipeline().addLast(new GroupQuitResponseHandler());
                        ch.pipeline().addLast(new GroupMessageResponseHandler());
                    }
                });

        // init 客户端启动端口
        String serverRunPortStr = RedisUtil.get(RedisKeys.SERVER_RUN_PORT);
        if (StringUtils.isBlank(serverRunPortStr)) {
            String tempLog = "[客户端启动] >>> 启动受限! 请检查服务端是否启动!";
            RedisUtil.lpush(RedisKeys.CLIENT_RUN_LOG, LogUtils.error(tempLog, this.getClass()));
            return;
        }

        connect(bootstrap, imConfig.getHost(), Integer.valueOf(serverRunPortStr), MAX_RETRY, loginRequest);
    }

    private void connect(Bootstrap bootstrap, String host, Integer port, int retry, LoginRequest loginRequest) {
        bootstrap.connect(host, port).addListener(future -> {
            if (future.isSuccess()) {
                String tempLog = String.format("[客户端启动] >>> 连接服务器成功! 服务端端口: %s", port);
                RedisUtil.lpush(RedisKeys.CLIENT_RUN_LOG, LogUtils.info(tempLog, this.getClass()));

                ChannelFuture channelFuture = (ChannelFuture) future;
                channelFuture.channel().writeAndFlush(loginRequest);
                return;
            }

            // 重试次数
            int bout = MAX_RETRY - retry + 1;
            if (retry == 0) {
                String tempLog = "[客户端启动] >>> 连接服务器失败! 重试次数达上限, 请检查服务端状态!";
                RedisUtil.lpush(RedisKeys.CLIENT_RUN_LOG, LogUtils.error(tempLog, this.getClass()));
                return;
            }

            String tempLog = String.format("[客户端启动] >>> 连接服务器失败! 重试第 %s 次... Error: %s; ", bout, future.cause().getMessage());
            RedisUtil.lpush(RedisKeys.CLIENT_RUN_LOG, LogUtils.error(tempLog, this.getClass()));
            bootstrap.config().group().schedule(() ->
                    connect(bootstrap, host, port, retry - 1, loginRequest), (1 << bout >> 1), TimeUnit.SECONDS);

        });
    }

    private void initGroup() {
        if (WORKER_GROUP == null) {
            WORKER_GROUP = new NioEventLoopGroup();
        }
    }

}
