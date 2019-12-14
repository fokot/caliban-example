package com.fokot

object utils {

  // WC means WithConfig :-)
  trait WC[A] {
    def config: A
  }
}
