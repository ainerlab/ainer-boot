package dev.ainer.module.knowledge.knowledge.application;

/** Knowledge scope（ADR-0044）：资源服务器链只做认证，scope 在应用服务内强制。 */
public final class KnowledgeAuthorities {

    public static final String READ = "knowledge.read";

    public static final String MANAGE = "knowledge.manage";

    private KnowledgeAuthorities() {
    }
}
