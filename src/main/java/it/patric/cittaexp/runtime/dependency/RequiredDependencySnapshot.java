package it.patric.cittaexp.runtime.dependency;

import java.util.List;

public record RequiredDependencySnapshot(List<ExternalDependencyStatus> dependencies) {

    public RequiredDependencySnapshot {
        dependencies = List.copyOf(dependencies);
    }

    public boolean allAvailable() {
        return dependencies.stream().allMatch(dep -> dep.state() == DependencyState.AVAILABLE);
    }
}
