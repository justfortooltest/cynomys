package cn.howardliu.monitor.cynomys.proxy.net;

import cn.howardliu.gear.zk.ZkClientFactoryBuilder;
import cn.howardliu.gear.zk.ZkConfig;
import cn.howardliu.monitor.cynomys.net.struct.Header;
import cn.howardliu.monitor.cynomys.net.struct.Message;
import cn.howardliu.monitor.cynomys.net.struct.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.lang3.CharEncoding;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static cn.howardliu.monitor.cynomys.proxy.config.SystemSetting.SYSTEM_SETTING;

/**
 * <br>created at 17-7-29
 *
 * @author liuxh
 * @version 0.0.1
 * @since 0.0.1
 */
public abstract class AbstractInfo2ZkHandler extends SimpleChannelInboundHandler<Message> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractInfo2ZkHandler.class);
    private final CuratorFramework zkClient;

    public AbstractInfo2ZkHandler() {
        this.zkClient = new ZkClientFactoryBuilder()
                .zkAddresses(SYSTEM_SETTING.getZkAddresses())
                .namespace(SYSTEM_SETTING.getZkNamespace())
                .config(ZkConfig.JuteMaxBuffer.key, 10 * 1024 * 1024)
                .build()
                .createClient();
    }

    protected void send(ChannelHandlerContext ctx, Message message, String prePath, MessageType resp) {
        Header header = message.getHeader();
        String path = prePath + "/" + header.getSysName() + "-" + header.getSysCode() + "-" + header.getTag();
        boolean success = true;
        String errMsg = null;
        try {
            Stat stat = zkClient.checkExists().forPath(path);
            if (stat == null) {
                try {
                    String data = header.getSysName() + "-" + header.getSysCode();
                    zkClient.create().creatingParentsIfNeeded().forPath(path, data.getBytes(CharEncoding.UTF_8));
                } catch (Exception e) {
                    stat = zkClient.checkExists().forPath(path);
                    if (stat == null) {
                        throw new RuntimeException("got an exception when creating path [" + path + "]");
                    }
                }
            }
            zkClient.setData().forPath(path, message.getBody().getBytes(CharEncoding.UTF_8));
        } catch (Exception e) {
            logger.error("got an exception when sending application info to zk", e);
            success = false;
            errMsg = e.toString();
        }

        StringBuilder body = new StringBuilder();
        if (success) {
            body.append("{success: true}");
        } else {
            body.append("{success: false, errMsg: \"").append(errMsg).append("\"}");
        }

        ctx.writeAndFlush(
                new Message()
                        .setHeader(new Header().setType(resp.value()))
                        .setBody(body.toString())
        );
    }
}
