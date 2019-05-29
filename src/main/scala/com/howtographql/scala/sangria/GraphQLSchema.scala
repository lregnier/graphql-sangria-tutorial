package com.howtographql.scala.sangria

import akka.http.scaladsl.model.DateTime
import com.howtographql.scala.sangria.models._
import sangria.ast.StringValue
import sangria.execution.deferred._
import sangria.macros.derive._
import sangria.schema.{Field, ListType, ObjectType, _}
import sangria.marshalling.sprayJson._
import spray.json.DefaultJsonProtocol._

object GraphQLSchema {

  // Scalar Types
  implicit val GraphQLDateTime = ScalarType[DateTime](
    "DateTime",
    coerceOutput = (dt, _) => dt.toString,
    coerceInput = {
      case StringValue(dt, _, _ ) => DateTime.fromIsoDateTimeString(dt).toRight(DateTimeCoerceViolation)
      case _ => Left(DateTimeCoerceViolation)
    },
    coerceUserInput = {
      case s: String => DateTime.fromIsoDateTimeString(s).toRight(DateTimeCoerceViolation)
      case _ => Left(DateTimeCoerceViolation)
    }
  )

  // Relations
  val linkByUserRel = Relation[Link, Int]("byUser", l => Seq(l.postedBy))
  val voteByUserRel = Relation[Vote, Int]("byUser", v => Seq(v.userId))
  val voteByLinkRel = Relation[Vote, Int]("byLink", v => Seq(v.linkId))

  // Interface Types
  val IdentifiableType = InterfaceType(
    "Identifiable",
    fields[Unit, Identifiable](
      Field("id", IntType, resolve = _.value.id)
    )
  )

  // Object Types
  lazy val UserType: ObjectType[Unit, User] =
    deriveObjectType[Unit, User](
      Interfaces(IdentifiableType),
      ReplaceField("createdAt", Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)),
      AddFields(
        Field("links", ListType(LinkType), resolve = c => linksFetcher.deferRelSeq(linkByUserRel, c.value.id)),
        Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(voteByUserRel, c.value.id))
      )
    )

  implicit val userHasId = HasId[User, Int](_.id)

  lazy val LinkType: ObjectType[Unit, Link] =
    deriveObjectType[Unit, Link](
      Interfaces(IdentifiableType),
      ReplaceField("createdAt", Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)),
      ReplaceField("postedBy",
        Field("postedBy", UserType, resolve = c => usersFetcher.defer(c.value.postedBy))
      ),
      AddFields(
        Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(voteByLinkRel, c.value.id))
      )
    )

  implicit val linkHasId = HasId[Link, Int](_.id)

  lazy val VoteType: ObjectType[Unit, Vote] =
    deriveObjectType[Unit, Vote](
      Interfaces(IdentifiableType),
      ReplaceField("createdAt", Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)),
      ExcludeFields("userId", "linkId"),
      AddFields(
        Field("user",  UserType, resolve = c => usersFetcher.defer(c.value.userId)),
        Field("link",  LinkType, resolve = c => linksFetcher.defer(c.value.linkId))
      )
    )

  implicit val voteHasId = HasId[Vote, Int](_.id)

  implicit val authProviderEmailFormat = jsonFormat2(AuthProviderEmail)
  implicit val authProviderSignupDataFormat = jsonFormat1(AuthProviderSignupData)

  implicit val AuthProviderEmailInputType: InputObjectType[AuthProviderEmail] = deriveInputObjectType[AuthProviderEmail](
    InputObjectTypeName("AUTH_PROVIDER_EMAIL")
  )

  lazy val AuthProviderSignupDataInputType: InputObjectType[AuthProviderSignupData] = deriveInputObjectType[AuthProviderSignupData]()

  // Fetchers
  val linksFetcher =
    Fetcher.rel(
      (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getLinks(ids),
      (ctx: MyContext, ids: RelationIds[Link]) => ctx.dao.getLinksByUserIds(ids(linkByUserRel))
    )

  val usersFetcher = Fetcher((ctx: MyContext, ids: Seq[Int]) => ctx.dao.getUsers(ids))

  val votesFetcher =
    Fetcher.rel(
      (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getVotes(ids),
      (ctx: MyContext, ids: RelationIds[Vote]) => ctx.dao.getVotesByRelationIds(ids)
    )

  // Resolvers
  val Resolver =
    DeferredResolver.fetchers(
      linksFetcher,
      usersFetcher,
      votesFetcher
    )

  // Queries
  val Id = Argument("id", IntType)
  val Ids = Argument("ids", ListInputType(IntType))

  val QueryType = ObjectType(
    "Query",
    fields[MyContext, Unit](
      Field("allLinks", ListType(LinkType), resolve = c => c.ctx.dao.allLinks),
      Field("link",
        OptionType(LinkType),
        arguments = List(Id),
        resolve = c => linksFetcher.deferOpt(c.arg(Id))
      ),
      Field("links",
        ListType(LinkType),
        arguments = List(Ids),
        resolve = c => linksFetcher.deferSeq(c.arg(Ids))
      ),
      Field("users",
        ListType(UserType),
        arguments = List(Ids),
        resolve = c => usersFetcher.deferSeq(c.arg(Ids))
      ),
      Field("votes",
        ListType(VoteType),
        arguments = List(Ids),
        resolve = c => votesFetcher.deferSeq(c.arg(Ids))
      )
    )
  )

  // Mutations
  val NameArg = Argument("name", StringType)
  val AuthProviderArg = Argument("authProvider", AuthProviderSignupDataInputType)
  val UrlArg = Argument("url", StringType)
  val DescriptionArg = Argument("description", StringType)
  val PostedByArg = Argument("postedBy", IntType)
  val LinkIdArg = Argument("linkId", IntType)
  val UserIdArg = Argument("userId", IntType)

  val Mutation = ObjectType(
    "Mutation",
    fields[MyContext, Unit](
      Field("createUser",
        UserType,
        arguments = List(NameArg, AuthProviderArg),
        resolve = c => c.ctx.dao.createUser(c.arg(NameArg), c.arg(AuthProviderArg))
      ),
      Field("createLink",
        LinkType,
        arguments = List(UrlArg, DescriptionArg, PostedByArg),
        resolve = c => c.ctx.dao.createLink(c.arg(UrlArg), c.arg(DescriptionArg), c.arg(PostedByArg))
      ),
      Field("createVote",
        VoteType,
        arguments = List(LinkIdArg, UserIdArg),
        resolve = c => c.ctx.dao.createVote(c.arg(LinkIdArg), c.arg(UserIdArg))
      )
    )
  )

  val SchemaDefinition = Schema(QueryType, Some(Mutation))

}