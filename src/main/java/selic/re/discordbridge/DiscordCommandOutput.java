package selic.re.discordbridge;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static selic.re.discordbridge.DiscordFormattingConverter.minecraftToDiscord;

public class DiscordCommandOutput implements CommandOutput {
    public static final ScheduledThreadPoolExecutor SCHEDULED_THREAD_POOL_EXECUTOR = new ScheduledThreadPoolExecutor(1);

    private final Message message;
    private final StringBuilder buffer = new StringBuilder();
    private final ReentrantLock lock = new ReentrantLock();
    private ScheduledFuture<?> scheduledFlush;

    public DiscordCommandOutput(Message message) {
        this.message = message;
    }

    private void flush() {
        lock.lock();
        try {
            final Queue<Message> messages = new MessageBuilder().append(buffer).buildAll(MessageBuilder.SplitPolicy.NEWLINE);
            for (Message reply : messages) {
                message.reply(reply).queue();
            }
            buffer.setLength(0);
        } finally {
            lock.unlock();
        }
        scheduledFlush = null;
    }

    @Override
    public void sendMessage(Text text) {
        lock.lock();
        try {
            if (scheduledFlush != null) {
                scheduledFlush.cancel(false);
            }
            if (buffer.length() > 0) {
                buffer.append("\n");
            }
            buffer.append(minecraftToDiscord(text));
            scheduledFlush = SCHEDULED_THREAD_POOL_EXECUTOR.schedule(this::flush, 100, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean shouldReceiveFeedback() {
        return true;
    }

    @Override
    public boolean shouldTrackOutput() {
        return true;
    }

    @Override
    public boolean shouldBroadcastConsoleToOps() {
        return false;
    }
}
