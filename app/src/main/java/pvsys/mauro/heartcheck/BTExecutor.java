package pvsys.mauro.heartcheck;

import android.support.v7.app.AppCompatActivity;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BTExecutor {

    private final static Logger LOG = new Logger(BTExecutor.class.getSimpleName());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    final BTDeviceChannel deviceChannel;
    private int taskCounter = 1;

    public Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private AtomicBoolean free = new AtomicBoolean(true);

    public BTExecutor(BTDeviceChannel deviceChannel) {
        this.deviceChannel = deviceChannel;
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                //LOG.info("iteration, free? " + free.get() + "queue: " + tasks.size());
                if(free.get()) {
                    Runnable r = tasks.poll();
                    if(r!=null) {
                        free.set(false);
                        LOG.info("executing task " + taskCounter);
                        r.run();
                    }
                }
                //LOG.info("iteration done");
            }
        }, 100, 100, TimeUnit.MILLISECONDS);

    }

    public void addTask(Runnable task) {
        tasks.add(task);
    }

    //TODO: improve task completed mechanism: is it really necessary to stop?
    public void taskCompleted() {
        taskCounter ++;
        free.set(true);
        LOG.info("task completed, queue: " + tasks.size());
    }

    public void writeCharacteristic(final UUID uuid, final byte[] value){
        addTask(new Runnable() {
            @Override
            public void run() {
                deviceChannel.writeCharacteristic(uuid, value);
            }
        });
    }

    public void readCharacteristic(final UUID uuid){
        addTask(new Runnable() {
            @Override
            public void run() {
                LOG.info("task read start");
                deviceChannel.readCharacteristic(uuid);
                LOG.info("task read done");
            }
        });
    }

    public void registerToCharacteristic(final UUID uuid, final boolean flag){
        addTask(new Runnable() {
            @Override
            public void run() {
                deviceChannel.registerToCharacteristic(uuid, flag);
            }
        });
    }
}