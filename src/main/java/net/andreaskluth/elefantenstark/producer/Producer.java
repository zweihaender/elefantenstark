package net.andreaskluth.elefantenstark.producer;

import static java.util.Objects.requireNonNull;
import static net.andreaskluth.elefantenstark.work.WorkItemDataMapSerializer.serialize;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import net.andreaskluth.elefantenstark.work.WorkItem;
import net.andreaskluth.elefantenstark.work.WorkItemDataMapSerializer;

public class Producer {

  public static final String INSERT_WORK_QUERY =
      "INSERT INTO queue (\"key\", \"group\", value, data_map, version) VALUES (?, ?, ?, ?, ?)";

  public boolean produce(Connection connection, WorkItem workItem) {
    requireNonNull(connection);
    requireNonNull(workItem);

    try (PreparedStatement statement = connection.prepareStatement(INSERT_WORK_QUERY)) {
      statement.setString(1, workItem.key());
      statement.setInt(2, workItem.group());
      statement.setString(3, workItem.value());
      statement.setBytes(4, serialize(workItem.workItemDataMap()));
      statement.setLong(5, workItem.version());
      return statement.execute();
    } catch (SQLException e) {
      throw new ProducerException(e);
    }
  }

  public class ProducerException extends RuntimeException {
    private static final long serialVersionUID = -1493847087387822207L;

    public ProducerException(Throwable cause) {
      super(cause);
    }
  }
}
