package net.axairlem.cg;

import java.util.ArrayList;

public class TaskQueue {
    private static final ArrayList<DelayedTask> taskQueue = new ArrayList<>();

    public static void add(Runnable task, int delayTicks) {
        taskQueue.add(new DelayedTask(task, delayTicks));
    }

    public static void tick() {

        if(taskQueue.isEmpty()) { return; }

        if(taskQueue.getFirst().isReady()) {
            taskQueue.getFirst().task.run();
            taskQueue.removeFirst();
        } else{
            taskQueue.getFirst().decrement();
        }
    }

    private static class DelayedTask {
        private final Runnable task;
        private int ticksLeft;

        public DelayedTask(Runnable task, int ticksLeft) {
            this.task = task;
            this.ticksLeft = ticksLeft;
        }

        public void decrement() {
            ticksLeft--;
        }

        public boolean isReady() {
            return ticksLeft <= 0;
        }
    }
}