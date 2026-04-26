package me.bill.fakePlayerPlugin.api;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface FppExtension {
  @NotNull String getName();

  @NotNull String getVersion();

  default @NotNull String getDescription() {
    return "";
  }

  default @NotNull List<String> getAuthors() {
    return List.of();
  }

  default @NotNull List<String> getDependencies() {
    return List.of();
  }

  default @NotNull List<String> getSoftDependencies() {
    return List.of();
  }

  default int getPriority() {
    return 100;
  }

  void onEnable(@NotNull FppApi api);

  void onDisable();
}
