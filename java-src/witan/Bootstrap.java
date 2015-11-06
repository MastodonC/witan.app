package witan;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

/**
 * We've had a lot of problems with aot. This bootstraps our clojure code from java avoiding that completely.
 */
class Bootstrap {

    public static void main(String[] args) {

        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("witan.bootstrap"));

        IFn bootstrap = Clojure.var("witan.bootstrap", "bootstrap");
        bootstrap.invoke(args);
    }
}
