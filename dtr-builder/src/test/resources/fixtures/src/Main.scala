package com.example

import java.util.List

object Main {
  def main(args: Array[String]): Unit = {
    println("Hello from DTR Builder test!")
  }

  def add(a: Int, b: Int): Int = a + b
}

class User(val name: String, val age: Int)

trait Repository[T] {
  def find(id: Long): Option[T]
  def save(entity: T): Unit
}

case class Person(name: String, age: Int)
