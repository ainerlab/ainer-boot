package dev.ainer.module.organization.orgdir.domain;

/** 主任职同期唯一；SECONDARY/ACTING 可并存（ADR-0042 §4.5）。 */
public enum AssignmentKind {
    PRIMARY,
    SECONDARY,
    ACTING
}
