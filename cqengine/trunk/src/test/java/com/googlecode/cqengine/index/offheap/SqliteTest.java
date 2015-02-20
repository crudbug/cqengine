package com.googlecode.cqengine.index.offheap;

import com.googlecode.cqengine.index.offheap.TemporaryDatabase.TemporaryInMemoryDatabase;
import com.googlecode.cqengine.index.offheap.support.DBQueries;
import com.googlecode.cqengine.index.offheap.support.DBUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.sql.*;

/**
 * @author niall.gallagher
 */
public class SqliteTest {

    @Rule
    public TemporaryInMemoryDatabase temporaryDatabase = new TemporaryInMemoryDatabase();

    @Test
    public void testConcurrentStatements() throws SQLException {
        Connection connection = temporaryDatabase.getConnectionManager(true).getConnection(null);
        final int NUM_ROWS = 10;
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS test_table (column1 INTEGER, column2 BLOB, PRIMARY KEY (column1, column2)) WITHOUT ROWID;");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS test_index ON test_table (column1);");
            stmt.close();
            PreparedStatement pstmt = connection.prepareStatement("INSERT INTO test_table (column1, column2) VALUES (?, ?);");
            for (int i = 0; i < NUM_ROWS; i++) {
                pstmt.setObject(1, i);
                pstmt.setObject(2, createBytes(i));
                pstmt.executeUpdate();
                pstmt.clearParameters();
            }
            pstmt.close();

            PreparedStatement rstmt1 = connection.prepareStatement("SELECT * FROM test_table WHERE column1 >= 3 AND column1 <= 5;");
            ResultSet rs1 = rstmt1.executeQuery();
            while (rs1.next()) {
                Integer column1 = rs1.getInt(1);
                byte[] column2 = rs1.getBytes(2);
                System.out.println("Processing: " + column1);
                if (column1 == 4) {
                    PreparedStatement rstmt2 = connection.prepareStatement("SELECT COUNT(column2) FROM test_table WHERE column1 >= 3 AND column1 <= 5 AND column2 = ?;");
                    rstmt2.setBytes(1, column2);
                    ResultSet rs2 = rstmt2.executeQuery();
                    Assert.assertTrue(rs2.next());
                    Integer count = rs2.getInt(1);
                    rs2.close();
                    rstmt2.close();
                    System.out.println("Count for 4: " + count);
                }
            }
            rs1.close();
            rstmt1.close();
        }
        finally {
            DBUtils.closeQuietly(connection);
        }
    }

    static byte[] createBytes(int rowNumber) {
        final int NUM_BYTES = 50;
        byte[] result = new byte[50];
        for (int i = 0; i < NUM_BYTES; i++) {
            result[i] = (byte) (rowNumber + i);
        }
        return result;
    }
}
