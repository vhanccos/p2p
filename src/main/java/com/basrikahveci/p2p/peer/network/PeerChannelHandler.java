package com.basrikahveci.p2p.peer.network;

import com.basrikahveci.p2p.peer.Config;
import com.basrikahveci.p2p.peer.Peer;
import com.basrikahveci.p2p.peer.network.message.FileMessage;
import com.basrikahveci.p2p.peer.network.message.Handshake;
import com.basrikahveci.p2p.peer.network.message.Message;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class PeerChannelHandler extends SimpleChannelInboundHandler<Message> {

    static final String SESSION_ATTRIBUTE_KEY = "session";

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerChannelHandler.class);

    static Attribute<Connection> getSessionAttribute(ChannelHandlerContext ctx) {
        return ctx.attr(AttributeKey.<Connection>valueOf(SESSION_ATTRIBUTE_KEY));
    }

    private final Config config;

    private final Peer peer;


    public PeerChannelHandler(Config config, Peer peer) {
        this.config = config;
        this.peer = peer;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Channel active {}", ctx.channel().remoteAddress());
        final Connection connection = new Connection(ctx);
        getSessionAttribute(ctx).set(connection);
        ctx.writeAndFlush(new Handshake(config.getPeerName(), peer.getLeaderName()));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Channel inactive {}", ctx.channel().remoteAddress());
        final Connection connection = getSessionAttribute(ctx).get();
        peer.handleConnectionClosed(connection);
    }

    @Override
public void channelRead0(final ChannelHandlerContext ctx, final Message message) throws Exception {
    LOGGER.debug("Message {} received from {}", message.getClass(), ctx.channel().remoteAddress());
    final Connection connection = getSessionAttribute(ctx).get();

    if (message instanceof FileMessage) {
        FileMessage fileMessage = (FileMessage) message;
        saveReceivedFile(fileMessage);
        LOGGER.info("File '{}' received from {}", fileMessage.getFileName(), fileMessage.getSenderName());
    } else {
        message.handle(peer, connection);
    }
}
private void saveReceivedFile(FileMessage fileMessage) {
    try {
        java.nio.file.Path dir = java.nio.file.Paths.get("received_files");
        java.nio.file.Files.createDirectories(dir);

        java.nio.file.Path filePath = dir.resolve(fileMessage.getFileName());
        java.nio.file.Files.write(filePath, fileMessage.getFileContent());

        LOGGER.info("File saved in: {}", filePath.toAbsolutePath());
    } catch (Exception e) {
        LOGGER.error("Error saving the received file", e);
    }
}

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOGGER.error("Channel failure " + ctx.channel().remoteAddress(), cause);
        ctx.close();
        peer.handleConnectionClosed(getSessionAttribute(ctx).get());
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (evt instanceof IdleStateEvent) {
            final IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                LOGGER.warn("Channel idle {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }

}
