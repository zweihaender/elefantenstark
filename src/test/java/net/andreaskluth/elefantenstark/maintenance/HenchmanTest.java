package net.andreaskluth.elefantenstark.maintenance;

import static net.andreaskluth.elefantenstark.PostgresSupport.withPostgresConnectionsAndSchema;
import static net.andreaskluth.elefantenstark.TestData.scheduleThreeWorkItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.andreaskluth.elefantenstark.JdbcSupport;
import net.andreaskluth.elefantenstark.consumer.Consumer;
import net.andreaskluth.elefantenstark.consumer.ConsumerTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class HenchmanTest {

  final Henchman henchman = Henchman.henchman();

  final Consumer consumer = Consumer.sessionScoped();
  final QueueMetrics queueMetrics = new QueueMetrics();

  @Test
  void deleteOldWorkItems() {
    withPostgresConnectionsAndSchema(
        connections -> {
          Connection connection = connections.get();

          scheduleThreeWorkItems(connection);
          consumeThreeWorkItems(connection);

          henchman.cleanupOldWorkItems(connection, Duration.ofHours(-1));

          assertEquals(0, queueMetrics.size(connection));
        });
  }

  @Test
  void unlockSingleEntry() {
    withPostgresConnectionsAndSchema(
        connections -> {
          Connection connection = connections.get();
          scheduleThreeWorkItems(connection);

          Optional<Long> next =
              consumer.next(
                  connection,
                  workItemContext -> {
                    henchman.unlockAdvisoryLock(connection, workItemContext.workItem().hash());
                    return queueMetrics.currentLocks(connection);
                  });

          assertEquals(Optional.of(0L), next);
        });
  }

  @Test
  void unlockAll() {
    withPostgresConnectionsAndSchema(
        connections -> {
          Connection connection = connections.get();
          scheduleThreeWorkItems(connection);

          Optional<Long> next =
              consumer.next(
                  connection,
                  workItemContext -> {
                    henchman.unlockAllAdvisoryLocks(connection);
                    return queueMetrics.currentLocks(connection);
                  });

          assertEquals(Optional.of(0L), next);
        });
  }

  /**
   * TODO: Think if this kind of test improves the overall quality.
   */
  @Test
  void unlockRaisesOnClosedConnection() {
    Executable scope =
        () ->
            withPostgresConnectionsAndSchema(
                connections -> {
                  Connection connection = connections.get();

                  JdbcSupport.close(connection);

                  henchman.unlockAllAdvisoryLocks(connection);
                });

    assertThrows(HenchmanException.class, scope);
  }

  private void consumeThreeWorkItems(Connection connection) {
    ConsumerTestSupport.capturingConsume(connection, consumer, new AtomicReference<>());
    ConsumerTestSupport.capturingConsume(connection, consumer, new AtomicReference<>());
    ConsumerTestSupport.capturingConsume(connection, consumer, new AtomicReference<>());
  }
}
