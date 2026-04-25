package me.bill.fakePlayerPlugin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

@Disabled("MockBukkit v1.21 is not yet compatible with this Paper 1.21.11 registry bootstrap in Maven tests")
class FppSchedulerTest {

  @AfterEach
  void tearDown() {
    if (MockBukkit.isMocked()) {
      MockBukkit.unmock();
    }
  }

  @Test
  void standardMockServerExposesFoliaSchedulers() {
    ServerMock server = MockBukkit.mock();

    assertNotNull(server.getRegionScheduler());
    assertNotNull(server.getGlobalRegionScheduler());
    assertNotNull(server.getAsyncScheduler());
    assertTrue(FppScheduler.isFolia());
  }

  @Test
  void fallsBackToBukkitSchedulerWhenFoliaSchedulersFail() {
    ServerMock server = MockBukkit.mock(new PaperLikeServerMock());
    PluginMock plugin = MockBukkit.createMockPlugin();
    AtomicInteger runs = new AtomicInteger();

    FppScheduler.runSync(plugin, runs::incrementAndGet);
    server.getScheduler().performOneTick();

    assertEquals(1, runs.get());
  }

  @Test
  void delayedTasksUseBukkitFallbackWhenFoliaSchedulersFail() {
    ServerMock server = MockBukkit.mock(new PaperLikeServerMock());
    PluginMock plugin = MockBukkit.createMockPlugin();
    AtomicInteger runs = new AtomicInteger();

    FppScheduler.runSyncLater(plugin, runs::incrementAndGet, 2L);
    server.getScheduler().performTicks(1);
    assertEquals(0, runs.get());

    server.getScheduler().performTicks(1);
    assertEquals(1, runs.get());
  }

  private static final class PaperLikeServerMock extends ServerMock {
    @Override
    public io.papermc.paper.threadedregions.scheduler.RegionScheduler getRegionScheduler() {
      throw new UnsupportedOperationException("Paper test server has no Folia region scheduler");
    }

    @Override
    public io.papermc.paper.threadedregions.scheduler.AsyncScheduler getAsyncScheduler() {
      throw new UnsupportedOperationException("Paper test server has no Folia async scheduler");
    }

    @Override
    public io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler getGlobalRegionScheduler() {
      throw new UnsupportedOperationException("Paper test server has no Folia global scheduler");
    }
  }
}
