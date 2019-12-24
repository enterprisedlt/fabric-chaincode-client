package org.enterprisedlt.fabric.client

import org.enterprisedlt.general.gson.TypeNameResolver
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class FabricChainCodeTest extends FunSuite {
    private val NamesResolver = new TypeNameResolver() {
        override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")

        override def resolveNameByType(clazz: Class[_]): String = "dummy"
    }


}


case class Dummy(
    name: String,
    value: String
)