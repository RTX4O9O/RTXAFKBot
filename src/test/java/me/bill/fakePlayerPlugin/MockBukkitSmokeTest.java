package me.bill.fakePlayerPlugin;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

@Disabled("MockBukkit v1.21 is not yet compatible with this Paper 1.21.11 registry bootstrap in Maven tests")
class MockBukkitSmokeTest {

  @AfterEach
  void tearDown() {
    if (MockBukkit.isMocked()) {
      MockBukkit.unmock();
    }
  }

  @Test
  void canCreateWorldAndPlayer() {
    ServerMock server = MockBukkit.mock();
    WorldMock world = server.addSimpleWorld("test-world");
    PlayerMock player = server.addPlayer("Tester");

    assertNotNull(world);
    assertNotNull(player);
    assertNotNull(server.getScheduler());
  }
}
