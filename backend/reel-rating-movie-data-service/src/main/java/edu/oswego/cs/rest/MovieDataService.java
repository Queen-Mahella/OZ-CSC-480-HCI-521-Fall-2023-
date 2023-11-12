package edu.oswego.cs.rest;

import edu.oswego.cs.rest.JsonClasses.Actor;
import edu.oswego.cs.rest.JsonClasses.Tag;
import edu.oswego.cs.rest.JsonClasses.Movie;
import edu.oswego.cs.rest.JsonClasses.Rating;
import edu.oswego.cs.rest.JsonClasses.Review;
import edu.oswego.cs.rest.JsonClasses.JSession;
import jakarta.enterprise.context.RequestScoped;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.List;

import com.ibm.websphere.security.jwt.JwtConsumer;

@Path("/")
@RequestScoped
public class MovieDataService {
  
  String AuthServiceUrl = System.getenv("AUTH_SERVICE_URL");

  /**
   * gets the username of the client request. Also authenticates the client using a JWT.
   * TODO double check if the above is correct
   *
   * @param request
   * @return String representation of the username within the request
   * @throws Exception
   */
  public String getUsername(String sessionId) throws Exception {
    Client authClient = ClientBuilder.newClient();
    WebTarget target = authClient.target(AuthServiceUrl + "/reel-rating-auth-service/jwt/generate/" + sessionId);
    Response response = target.request().get();
    String value = response.readEntity(String.class);
    if (value == null || value == "") {
       return null;
    }
    JwtConsumer jwtConsumer = JwtConsumer.create("reel_rating_token");
    String username = jwtConsumer.createJwt(value)
               .getClaims()
               .getAllClaims().get("upn").toString();
    authClient.close();
    return username;
  }

  /**
   * Create Endpoints
   */

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/movie/create")
  public Response createMovieEndPoint(@Context HttpServletRequest request, Movie movie) throws Exception {
    String username = getUsername(movie.getJSESSIONID());
    if (username == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController db = new DatabaseController();
    db.createMovie(movie.getTitle(), movie.getDirector(), movie.getReleaseDate(), movie.getRuntime(), movie.getWriters(), movie.getSummary());
    return Response.ok().build();
  }


  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/movie/getByTitle/{title}")
  public Response getMoviesWithTitleEndPoint(@Context HttpServletRequest request, @PathParam("title") String title, JSession jsession) throws Exception {
    String username = getUsername(jsession.getJSESSIONID());
    if (username == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    List<Movie> movies = dbc.getMoviesWithTitle(title);
    return Response.ok(movies).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/movie/getByTagName/{TagName}")
  public Response getMoviesWithTagName(@Context HttpServletRequest request, @PathParam("TagName") String tagName, JSession jsession) throws Exception {
    String username = getUsername(jsession.getJSESSIONID());
    if (username == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    List<Movie> movies = dbc.getMoviesWithTag(tagName);
    return Response.ok(movies).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/movie/getByRatingCategoryName/{ratingCategoryName}")
  public Response getMoviesWithRatingCategoryName(@Context HttpServletRequest request, @PathParam("ratingCategoryName") String ratingCategoryName, JSession jsession) throws Exception {
    String username = getUsername(jsession.getJSESSIONID());
    if (username == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    List<Movie> movies = dbc.getMoviesWithRatingCategory(ratingCategoryName);
    return Response.ok(movies).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/movie/getByActor/{actorId}")
  public Response getMoviesWithActorId(@Context HttpServletRequest request, @PathParam("actorId") String actorId, JSession jsession) throws Exception {
    String username = getUsername(jsession.getJSESSIONID());
    if (username == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    List<Movie> movies = dbc.getMoviesWithActor(actorId);
    return Response.ok(movies).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/movie/getMoviesWithMostReviews")
  public Response getMoviesWithMostReviews(@Context HttpServletRequest request, JSession session) throws Exception {
    String username = getUsername(session.getJSESSIONID());
    if (username == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    int numMovies = 12;
    List<Movie> movies = dbc.getMoviesWithMostReviews(numMovies);
    for ( Movie m : movies ) {
      // get the most popular rating and average for each movie
      Rating r = dbc.getMostPopularAggregatedRatingForMovie(m.getId());

      // set the appropriate fields for each movie
      m.setMostPopularRatingCategory(r.getRatingName());
      m.setMostPopRatingUpperBound(r.getUpperbound());
      m.setMostPopRatingAvg(r.getUserRating());

      // names of three tags from the movie
      List<Tag> tagList = dbc.getThreeTags(m.getId());
      ArrayList<String> tagNameList = new ArrayList<>();
      for (Tag t: tagList ) {
        tagNameList.add(t.getTagName());
      }
      m.setAttachedTags(tagNameList);
    }
    return Response.ok(movies).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/movie/getRecentReleaseMovies")
  public Response getRecentReleaseMoviesEndpoint(@Context HttpServletRequest request, JSession session) throws Exception {
    String username = getUsername(session.getJSESSIONID());
    if (username == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    int numMovies = 12;
    // get a List of the #numMovies most recent releases.
    List<Movie> movies = dbc.getRecentReleaseMovies(numMovies);
    for ( Movie m : movies ) {
      // get the most popular rating and average for each movie
      Rating r = dbc.getMostPopularAggregatedRatingForMovie(m.getId());

      // set the appropriate fields for each movie
      //Do this if not null, do nothing if null.
      if (r != null) {
        m.setMostPopularRatingCategory(r.getRatingName());
        m.setMostPopRatingUpperBound(r.getUpperbound());
        m.setMostPopRatingAvg(r.getUserRating());
      }

      // names of three tags from the movie
      List<Tag> tagList = dbc.getThreeTags(m.getId());
      ArrayList<String> tagNameList = new ArrayList<>();
      for (Tag t: tagList ) {
        tagNameList.add(t.getTagName());
      }
      m.setAttachedTags(tagNameList);
    }
    return Response.ok(movies).build();
  }


  /**
   * Image methods
   */

  /**
   *
   * @param request TODO describe what this is
   * @param movieId unique MongoDB id for a movie
   * @return stock image from the pre-populated database collection
   * @throws Exception TODO not sure if that is needed
   */
  @GET
  @Produces("image/webp")
  @Path("/movie/getMovieImage/{movieId}")
  public Response getMovieImage(@Context HttpServletRequest request, @PathParam("movieId") String movieId) throws Exception {
    //String requesterUsername = getUsername(request);
    //if (requesterUsername == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    String imageId = dbc.getMovieImageId(movieId);
    if (imageId == null) return Response.status(Status.NOT_FOUND).build();
    byte[] image = dbc.getStockImage(imageId);
    return Response.ok(image).build();
  }

  /**
   * Returns a list of movies that have been rated using a specific category
   * @param request
   * @param rating
   * @return
   * @throws Exception
   */
  @POST
  @Path("/movie/getByRatingCategory")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getMoviesByRatingCategory(@Context HttpServletRequest request, Rating rating) throws Exception {
    String requesterUsername = getUsername(rating.getJSESSIONID());
    if (requesterUsername == null) { return Response.status(Response.Status.UNAUTHORIZED).build(); }
    DatabaseController dbc = new DatabaseController();
    List<Movie> movies = dbc.getMoviesWithRatingCategory(rating.getRatingName(), rating.getUpperbound());
    return Response.ok(movies).build();
  }
}