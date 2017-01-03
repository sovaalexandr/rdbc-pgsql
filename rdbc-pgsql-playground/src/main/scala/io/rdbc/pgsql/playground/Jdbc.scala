package io.rdbc.pgsql.playground

import java.sql.DriverManager
import java.util.Properties

object Jdbc extends App {

  val props = new Properties()
  props.setProperty("user", "povder")
  props.setProperty("password", "povder")
  val conn = DriverManager.getConnection("jdbc:postgresql://localhost/povder", props)
  try {
    val stmt = conn.prepareStatement("insert into test(x) values (?)")
    (1 to 100).foreach { i =>
      stmt.setInt(1, i)
      stmt.addBatch()
    }
    stmt.executeBatch()

  } finally {
    conn.close()
  }

}

object JdbcSelect extends App {

  val props = new Properties()
  props.setProperty("user", "povder")
  props.setProperty("password", "povder")
  val conn = DriverManager.getConnection("jdbc:postgresql://localhost/povder", props)
  try {
    (1 to 100).foreach { i =>
      val start = System.nanoTime()
      val stmt = conn.prepareStatement("select x from test")
      val rs = stmt.executeQuery()
      while (rs.next()) {
        rs.getInt("x")
      }
      val time = System.nanoTime() - start
      println(s"$i time = ${time / 1000000.0}ms")
    }
  } finally {
    conn.close()
  }

}

object JdbcTypeTest extends App {

  val props = new Properties()
  props.setProperty("user", "povder")
  props.setProperty("password", "povder")
  val conn = DriverManager.getConnection("jdbc:postgresql://localhost/povder?binaryTransferEnable=bool", props)
  try {
    val stmt = conn.prepareStatement("select x from decimal_test")
    val rs = stmt.executeQuery()
    while (rs.next()) {
      println(rs.getObject(1))
    }

  } finally {
    conn.close()
  }

}
