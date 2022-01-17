package examples;

import io.smallrye.stork.Stork;

public class StorkEntryPointExample {

    public static void main(String[] args) {
        Stork.initialize();
        Stork stork = Stork.getInstance();
        // ...
        Stork.shutdown();
    }

}
