package com.howtographql.scala.sangria

import sangria.schema.{Field, ListType, ObjectType}
import models._
// #
import sangria.schema._
import sangria.macros.derive._

object GraphQLSchema {

  implicit val LinkType = deriveObjectType[Unit, Link]()

  val Id = Argument("id", IntType)
  val Ids = Argument("ids", ListInputType(IntType))

  val QueryType = ObjectType(
    "Query",
    fields[MyContext, Unit](
      Field("allLinks", ListType(LinkType), resolve = c => c.ctx.dao.allLinks),
      Field("link", //1
        OptionType(LinkType), //2
        arguments = Id :: Nil, //3
        resolve = c => c.ctx.dao.getLink(c.arg[Int]("id")) //4
      ),
      Field("links", //1
        ListType(LinkType), //2
        arguments = Ids :: Nil, //3
        resolve = c => c.ctx.dao.getLinks(c.arg[Seq[Int]]("ids")) //4
      )
    )
  )

  val SchemaDefinition = Schema(QueryType)
}