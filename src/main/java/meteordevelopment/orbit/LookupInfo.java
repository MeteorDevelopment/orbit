package meteordevelopment.orbit;

import java.lang.invoke.MethodHandles;

public class LookupInfo {
    public final String packagePrefix;
    public final MethodHandles.Lookup lookup;

    public LookupInfo(String packagePrefix, MethodHandles.Lookup lookup) {
        this.packagePrefix = packagePrefix;
        this.lookup = lookup;
    }
}
