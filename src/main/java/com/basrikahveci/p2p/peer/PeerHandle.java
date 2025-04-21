package com.basrikahveci.p2p.peer;
import java.io.File;
import java.nio.file.Files;


import com.basrikahveci.p2p.peer.network.PeerChannelHandler;
import com.basrikahveci.p2p.peer.network.PeerChannelInitializer;
import com.basrikahveci.p2p.peer.network.message.FileMessage;
import com.basrikahveci.p2p.peer.service.ConnectionService;
import com.basrikahveci.p2p.peer.service.LeadershipService;
import com.basrikahveci.p2p.peer.service.PingService;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

public class PeerHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerHandle.class);


    private final Config config;

    private final int portToBind;

    private final EventLoopGroup acceptorEventLoopGroup = new NioEventLoopGroup(1);

    private final EventLoopGroup networkEventLoopGroup = new NioEventLoopGroup(6);

    private final EventLoopGroup peerEventLoopGroup = new NioEventLoopGroup(1);

    private final ObjectEncoder encoder = new ObjectEncoder();

    private final Peer peer;

    private Future keepAliveFuture;

    private Future timeoutPingsFuture;

    public PeerHandle(Config config, int portToBind) {
        this.config = config;
        this.portToBind = portToBind;
        final ConnectionService connectionService = new ConnectionService(config, networkEventLoopGroup, peerEventLoopGroup, encoder);
        final LeadershipService leadershipService = new LeadershipService(connectionService, config, peerEventLoopGroup);
        final PingService pingService = new PingService(connectionService, leadershipService, config);
        this.peer = new Peer(config, connectionService, pingService, leadershipService);
    }

    public String getPeerName() {
        return config.getPeerName();
    }

    public ChannelFuture start() throws InterruptedException {
        ChannelFuture closeFuture = null;

        final PeerChannelHandler peerChannelHandler = new PeerChannelHandler(config, peer);
        final PeerChannelInitializer peerChannelInitializer = new PeerChannelInitializer(config, encoder,
                peerEventLoopGroup, peerChannelHandler);
        final ServerBootstrap peerBootstrap = new ServerBootstrap();
        peerBootstrap.group(acceptorEventLoopGroup, networkEventLoopGroup).channel(NioServerSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_BACKLOG, 100).handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(peerChannelInitializer);

        final ChannelFuture bindFuture = peerBootstrap.bind(portToBind).sync();

        if (bindFuture.isSuccess()) {
            LOGGER.info("{} Successfully bind to {}", config.getPeerName(), portToBind);
            final Channel serverChannel = bindFuture.channel();

            final SettableFuture<Void> setServerChannelFuture = SettableFuture.create();
            peerEventLoopGroup.execute(() -> {
                try {
                    peer.setBindChannel(serverChannel);
                    setServerChannelFuture.set(null);
                } catch (Exception e) {
                    setServerChannelFuture.setException(e);
                }
            });

            try {
                setServerChannelFuture.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.error("Couldn't set bind channel to server " + config.getPeerName(), e);
                System.exit(-1);
            }

            final int initialDelay = Peer.RANDOM.nextInt(config.getKeepAlivePeriodSeconds());

            this.keepAliveFuture = peerEventLoopGroup.scheduleAtFixedRate((Runnable) peer::keepAlivePing, initialDelay, config.getKeepAlivePeriodSeconds(), SECONDS);

            this.timeoutPingsFuture = peerEventLoopGroup.scheduleAtFixedRate((Runnable) peer::timeoutPings, 0, 100, TimeUnit.MILLISECONDS);

            closeFuture = serverChannel.closeFuture();
        } else {
            LOGGER.error(config.getPeerName() + " could not bind to " + portToBind, bindFuture.cause());
            System.exit(-1);
        }

        return closeFuture;
    }

    public CompletableFuture<Collection<String>> ping() {
        final CompletableFuture<Collection<String>> future = new CompletableFuture<>();
        peerEventLoopGroup.execute(() -> peer.ping(future));
        return future;
    }

    public CompletableFuture<Void> leave() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        peerEventLoopGroup.execute(() -> peer.leave(future));
        if (keepAliveFuture != null && timeoutPingsFuture != null) {
            keepAliveFuture.cancel(false);
            timeoutPingsFuture.cancel(false);
            keepAliveFuture = null;
            timeoutPingsFuture = null;
        }
        return future;
    }

    public void scheduleLeaderElection() {
        peerEventLoopGroup.execute(peer::scheduleElection);
    }

    public CompletableFuture<Void> connect(final String host, final int port) {
        final CompletableFuture<Void> connectToHostFuture = new CompletableFuture<>();

        peerEventLoopGroup.execute(() -> peer.connectTo(host, port, connectToHostFuture));

        return connectToHostFuture;
    }

    public void disconnect(final String peerName) {
        peerEventLoopGroup.execute(() -> peer.disconnect(peerName));
    }
    public void sendFileToPeer(String peerName, String filePath) throws Exception {
        File file = new File(filePath);
    
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
    
        byte[] content = Files.readAllBytes(file.toPath());
        String fileName = file.getName();
    
        FileMessage message = new FileMessage(fileName, content, config.getPeerName());
    
        peerEventLoopGroup.execute(() -> peer.sendFileMessage(peerName, message));
    }
}
