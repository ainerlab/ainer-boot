package dev.ainer.module.task.tasks.application;

/** 任务模块 scope（ADR-0047）。 */
public final class TaskAuthorities {

    public static final String READ = "task.read";
    public static final String MANAGE = "task.manage";
    public static final String SUBMIT = "task.submit";

    private TaskAuthorities() {
    }
}
