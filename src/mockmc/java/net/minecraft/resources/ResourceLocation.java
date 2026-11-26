package net.minecraft.resources;

/** Minimal stand-in used to validate mod ids / resource namespaces. */
public final class ResourceLocation {

    private final String namespace;
    private final String path;

    public ResourceLocation(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static boolean isValidNamespace(String value) {
        return value != null && value.matches("[a-z0-9_.-]+");
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
