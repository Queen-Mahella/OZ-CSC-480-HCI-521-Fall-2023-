package edu.oswego.cs.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.mongodb.client.model.Sorts.descending;

import org.bson.BsonDateTime;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import edu.oswego.cs.rest.JsonClasses.Actor;
import edu.oswego.cs.rest.JsonClasses.Movie;
import edu.oswego.cs.rest.JsonClasses.Rating;
import edu.oswego.cs.rest.JsonClasses.Review;

public class DatabaseController {
  String mongoDatabaseName = System.getenv("MONGO_MOVIE_DATABASE_NAME");
  String mongoURL = System.getenv("MONGO_MOVIE_URL");

  public MongoDatabase getMovieDatabase() {
    MongoClient mongoClient = MongoClients.create(mongoURL);
    return mongoClient.getDatabase(mongoDatabaseName);
  }

  /**
   * get[DatabaseEntity]Collection methods return the specified collection of entities. These collections can then
   * be queried and updated by the other CRUD operations.
   */
  public MongoCollection<Document> getTagCollection() {
    return getMovieDatabase().getCollection("tags");
  }

  public MongoCollection<Document> getMovieCollection() {
    return getMovieDatabase().getCollection("movies");
  }

  public MongoCollection<Document> getActorCollection() {
    return getMovieDatabase().getCollection("actors");
  }

  public MongoCollection<Document> getRatingCollection() {
    return getMovieDatabase().getCollection("ratings");
  }

  public MongoCollection<Document> getUserAssociatedRatingCollection() {
    return getMovieDatabase().getCollection("userAssociatedRatings");
  }

  public MongoCollection<Document> getUserAssociatedTags() {
    return getMovieDatabase().getCollection("userAssociatedTags");
  }

  public MongoCollection<Document> getReviewCollection() {
    return getMovieDatabase().getCollection("reviews");
  }

  /**
   * Image methods are used to store, edit, and retrieve images to display within the application. Due to MongoDB's
   * approach to storing images collections cannot be used. Instead GridFSBuckets are used and Mongo handles the
   * underlying splitting and storing of data.
   *
   */

  // total number of stock images being stored. Used to grab at random.
  int numMovieImages = 3;

  public GridFSBucket getStockImageBucket() {
    return GridFSBuckets.create(getMovieDatabase(), "stockMovieImages");
  }

  /**
   * Called by the MovieDataService generateStockImages() to load the pre-selected images into the database.
   */
  public void storeStockImages() {
    GridFSBucket gridFSBucket = getStockImageBucket();
    for (int i = 1; i <= numMovieImages; i++) {
      // create a name to store the image
      String movieFileName = "stockImage" + i + ".jpg";
      String movieImagePath = "images/" + movieFileName;
      // attempt to grab and upload the image
      try {
        File file = new File(this.getClass().getClassLoader().getResource(movieImagePath).getFile());
        InputStream image = new FileInputStream(file);
        gridFSBucket.uploadFromStream(movieFileName, image);
      // if the image file cannot be found
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }
  }

  public String getRandomImageId() {
    ThreadLocalRandom random = ThreadLocalRandom.current();

    // Account for movieImages starting at an index of 1.
    int movieNumber = random.nextInt(numMovieImages) + 1;
    String movieFileName = "stockImage" + movieNumber + ".jpg";
    GridFSBucket gridFSBucket = getStockImageBucket();
    Bson query = Filters.eq("filename", movieFileName);
    return gridFSBucket.find(query).first().getObjectId().toHexString();
  }


  public byte[] getStockImage(String hexId) {
    // establish the images hex id and image bucket
    ObjectId stockImageId = new ObjectId(hexId);
    GridFSBucket gridFSBucket = getStockImageBucket();

    // open the download stream and grab the image
    GridFSDownloadStream downloadImageStream = gridFSBucket.openDownloadStream(stockImageId);
    int fileLength = (int)downloadImageStream.getGridFSFile().getLength();
    byte[] imageBytes = new byte[fileLength];
    downloadImageStream.read(imageBytes);
    return imageBytes;
  }

  public String getMovieImageId(String movieId) {
    return getMovieDocumentWithHexId(movieId).getString("movieImageId");
  }

  /**
   * update operations are used to change individual fields within database entities. For example
   * <code>updateMovieTitle</code> goes through all parts of the database that contain a movies title and updates them
   * to the new provided title. The unique MongoDB ids are crucial for these methods so that we do not lose track of
   * which item we are editing.
   */

  /**
   * Updates the movie title in the movies collection, tag collection, actor collection,
   * ratings collection, userAssociatedRatings collections, and reviews collection
   * @param id movie objects unique identification number
   * @param movieTitle String of new movie title to update to
   */
  public void updateMovieTitle(String id, String movieTitle) {
    MongoCollection<Document> movies = getMovieCollection();
    Bson idFilter = Filters.eq("id", id);
    String oldMovieTitle = movies.find(idFilter).first().getString("title");
    Bson updateTitle = Updates.set("title", movieTitle);
    movies.updateOne(idFilter, updateTitle);

    MongoCollection<Document> tags = getTagCollection();
    Bson movieTitleFilter = Filters.eq("movieTitle", oldMovieTitle);
    Bson updateMovieTitle = Updates.set("movieTitle", movieTitle);
    tags.updateMany(movieTitleFilter, updateMovieTitle);

    MongoCollection<Document> actors = getActorCollection();
    Bson movieTitleFilterForActor = Filters.eq("movies", oldMovieTitle);
    Bson updateMovieTitleForActor = Updates.set("movies", movieTitle);
    actors.updateMany(movieTitleFilterForActor, updateMovieTitleForActor);

    MongoCollection<Document> ratings = getRatingCollection();
    Bson movieTitleFilterForRatings = Filters.eq("movieTitle", oldMovieTitle);
    Bson updateMovieTitleForRatings = Updates.set("movieTitle", movieTitle);
    ratings.updateMany(movieTitleFilterForRatings, updateMovieTitleForRatings);

    MongoCollection<Document> userAssocRatings = getUserAssociatedRatingCollection();
    Bson movieTitleFilterForUserAssocRatings = Filters.eq("movieTitle", oldMovieTitle);
    Bson updateMovieTitleForUserAssocRatings = Updates.set("movieTitle", movieTitle);
    userAssocRatings.updateMany(movieTitleFilterForUserAssocRatings, updateMovieTitleForUserAssocRatings);

    MongoCollection<Document> reviews = getReviewCollection();
    Bson movieTitleFilterForReviews = Filters.eq("movieTitle", oldMovieTitle);
    Bson updateMovieTitleForReviews = Updates.set("movieTitle", movieTitle);
    reviews.updateMany(movieTitleFilterForReviews, updateMovieTitleForReviews);
  }

  public void updateMovieDocument(String hexId, Document movieDocument) {
    var movieCollection = getMovieCollection();
    ObjectId movieId = new ObjectId(hexId);
    var filter = Filters.eq("_id", movieId);
    movieCollection.updateOne(filter, movieDocument);
  }

  public void updateDirector(String id, String director) {
    MongoCollection<Document> movies = getMovieCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson updateDirector = Updates.set("director", director);
    movies.updateOne(idFilter, updateDirector);
  }

  public void updateReleaseDate(String id, String releaseDate) {
    MongoCollection<Document> movies = getMovieCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson updateReleaseDate = Updates.set("releaseDate", releaseDate);
    movies.updateOne(idFilter, updateReleaseDate);
  }

  public void updateRunTime(String id, String runTime) {
    MongoCollection<Document> movies = getMovieCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson updateRunTime = Updates.set("runTime", runTime);
    movies.updateOne(idFilter, updateRunTime);
  }

  public void updatePlotSummary(String id, String plotSummary) {
    MongoCollection<Document> movies = getMovieCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson updatePlotSummary = Updates.set("plotSummary", plotSummary);
    movies.updateOne(idFilter, updatePlotSummary);
  }

  public void updateActor(String id, String name, String dob, List<String> movies) {
    MongoCollection<Document> actors = getActorCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson updateName = Updates.set("name", name);
    actors.updateOne(idFilter, updateName);
    Bson updateDOB = Updates.set("dob", dob);
    actors.updateOne(idFilter, updateDOB);
    Bson removeMovies = Updates.unset("movies");
    actors.updateOne(idFilter, removeMovies);
    Bson addMovies = Updates.pushEach("movies", movies);
    actors.updateOne(idFilter, addMovies);
  }

  public void updateActorName(String id, String name) {
    MongoCollection<Document> actors = getActorCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson updateName = Updates.set("name", name);
    actors.updateOne(idFilter, updateName);
  }

  public void updateActorDob(String id, String dob) {
    MongoCollection<Document> actors = getActorCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson updateDOB = Updates.set("dob", dob);
    actors.updateOne(idFilter, updateDOB);
  }

  // Allows bulk change of movies
  public void updateActorMovies(String id, List<String> movies) {
    MongoCollection<Document> actors = getActorCollection();
    Bson idFilter = Filters.eq("id", id);
    Bson removeMovies = Updates.unset("movies");
    actors.updateOne(idFilter, removeMovies);
    Bson addMovies = Updates.pushEach("movies", movies);
    actors.updateOne(idFilter, addMovies);
  }

  public void updateRatingCategoryName(String ratingCategoryId, String ratingName) {
    MongoCollection<Document> ratings = getRatingCollection();
    Bson idFilter = Filters.eq("ratingCategoryId", ratingCategoryId);
    Bson updateRatingName = Updates.set("ratingName", ratingName);
    String oldRatingName = ratings.find(idFilter).first().getString("ratingName");
    ratings.updateOne(idFilter, updateRatingName);

    MongoCollection<Document> userAssocRatings = getUserAssociatedRatingCollection();
    Bson ratingNameFilter = Filters.eq("ratingName", oldRatingName);
    userAssocRatings.updateOne(ratingNameFilter, updateRatingName);
  }

  // Could just call deleteUserRating then createUserRating
  public void updateUserRating(String ratingCategoryId, String ratingName, String movieTitle, String userName, String userRating) {
    MongoCollection<Document> ratings = getRatingCollection();
    Bson idFilter = Filters.eq("ratingCategoryId", ratingCategoryId);
    Bson userNameFilter = Filters.eq("userName", userName);
    Bson idAndUserName = Filters.and(idFilter, userNameFilter);
    Document oldRating = ratings.find(idAndUserName).first();
    String oldRatingName = oldRating.getString("ratingName");
    String oldMovieTitle = oldRating.getString("movieTitle");
    String oldUserRating = oldRating.getString("userRating");

    Bson movieTitleAndUserRatingAndUserName = Filters.and(userNameFilter, Filters.eq("movieTitle", oldMovieTitle), Filters.eq("userRating", oldUserRating));
    ratings.deleteOne(movieTitleAndUserRatingAndUserName);
  }

  public void updateCategoryRatingName(String ratingCategoryId, String ratingName) {
    MongoCollection<Document> ratings = getRatingCollection();
    Bson idFilter = Filters.eq("ratingCategoryId", ratingCategoryId);
    String oldRatingName = ratings.find(idFilter).first().getString("ratingName");
    Bson updateRatingName = Updates.set("ratingName", ratingName);
    ratings.updateOne(idFilter, updateRatingName);

    MongoCollection<Document> userAssocRatings = getUserAssociatedRatingCollection();
    Bson oldNameFilter = Filters.eq("ratingName", oldRatingName);
    userAssocRatings.updateMany(oldNameFilter, updateRatingName);
  }

  public void updateUserRating(String username, String ratingName, String movieTitle, String userRating) {
    MongoCollection<Document> ratings = getRatingCollection();
    Bson userNameFilter = Filters.eq("userName", username);
    Bson ratingNameFilter = Filters.eq("ratingName", ratingName);
    Bson movieTitleFilter = Filters.eq("movieTitle", movieTitle);
    Bson userNameAndRatingNameAndMovieTitleFilter = Filters.and(userNameFilter, ratingNameFilter, movieTitleFilter);
    Bson updateUserRating = Updates.set("userRating", userRating);
    ratings.updateOne(userNameAndRatingNameAndMovieTitleFilter, updateUserRating);

    MongoCollection<Document> userAssocRatings = getUserAssociatedRatingCollection();
    userAssocRatings.updateOne(userNameAndRatingNameAndMovieTitleFilter, updateUserRating);
  }

  public void updateReviewTitle(String movieTitle, String username, String reviewTitle) {
    MongoCollection<Document> reviews = getReviewCollection();
    Bson userNameFilter = Filters.eq("userName", username);
    Bson movieTitleFilter = Filters.eq("movieTitle", movieTitle);
    Bson userNameAndMovieTitleFilter = Filters.and(userNameFilter, movieTitleFilter);
    Bson updateReviewTitle = Updates.set("reviewTitle", reviewTitle);
    reviews.updateOne(userNameAndMovieTitleFilter, updateReviewTitle);
  }

  public void updateReviewDescription(String movieTitle, String username, String reviewDescription) {
    MongoCollection<Document> reviews = getReviewCollection();
    Bson userNameFilter = Filters.eq("userName", username);
    Bson movieTitleFilter = Filters.eq("movieTitle", movieTitle);
    Bson userNameAndMovieTitleFilter = Filters.and(userNameFilter, movieTitleFilter);
    Bson updateReviewDesc = Updates.set("reviewDescription", reviewDescription);
    reviews.updateOne(userNameAndMovieTitleFilter, updateReviewDesc);
  }

  public void updateReview(String movieTitle, String username, String reviewTitle, String reviewDescription) {
    updateReviewTitle(movieTitle, username, reviewTitle);
    updateReviewDescription(movieTitle, username, reviewDescription);
  }

  /**
   * Create CRUD operations
   *
   * The following methods can be called by endpoints or internally to create and add to the database. Methods check
   * for and do not permit duplicated. Many items are identified by their unique hex id to prevent double entries.
   */

  /**
   * Users are not allowed to create a tag for a movie that does not already exist. If the movie does not exist,
   * nothing happens.
   *
   * @param tagName String of the proposed tags name. For example, "Western" or "Grandpa Approved"
   * @param movieIdHexString movie unique MongoDB identifier
   */
  public void createTag(String tagName, String movieIdHexString) {
    // get the collections
    MongoCollection<Document> tagCollection = getTagCollection();
    MongoCollection<Document> movieCollection = getMovieCollection();

    // attempt to grab the movie using its unique MongoDB id
    ObjectId movieId = new ObjectId(movieIdHexString);
    Document movie = movieCollection.find(Filters.eq("_id", movieId)).first();

    // if the movie does not exist do nothing
    if (movie == null) { return; }
    // grab the two possible iterations of the tag that could exist
    // TODO Do we want these to be grabbed using movie titles
    Document existsAndTagged = tagCollection.find(Filters.eq("movieTitles", movie.getString("title"))).first();
    Document existingTag = tagCollection.find(Filters.eq("tagName", tagName)).first();

    // if the tag exists and the movie is already tagged
    if (null != existsAndTagged) {      }
    // if the tag exists and the movie is not tagged
    else if (null != existingTag) {
      // if the movie exists
      if (null != movie) {
        // push the movieName to the tag list
        Bson tagUpdateOperation = Updates.push("movieTitles", movie.getString("title"));
        tagCollection.updateOne(existingTag, tagUpdateOperation);
        // push the tagName to the movie list
        Bson movieUpdateOperation = Updates.push("tagNames", tagName);
        movieCollection.updateOne(movie, movieUpdateOperation);
      }
      // if the movie does not exist do nothing
      else {    }
    }
    // if the tag does not exist
    else {
      // if the movie exists
      if (null != movie) {
        // create the tag and add to the collection
        Document newTag = new Document("tagName", tagName).append("movieTitles", movie.getString("title"));
        tagCollection.insertOne(newTag);
        // push the tagName to the movie list
        Bson movieUpdateOperation = Updates.push("tagName", tagName);
        movieCollection.updateOne(movie, movieUpdateOperation);
      }
      // if the movie does not exist
      else {    }
    }
  }

  /**
   * Creates and adds a rating object associated with a movie to the database. Employs a series
   * of checks to make sure the movie exists and the ratingCategory does not already exist.
   * @param ratingName Name of the rating category. For example, "How Harrison Ford is it", "Stickiness"
   * @param movieIdHexString movie unique MongoDB identifier
   * @param username user to associate with the rating
   * @param userRating value assigned by the user
   * @param upperbound upperbound of the rating scale. 0 < upperbound < 11
   */
  public void createRating(String ratingName, String userRating, String upperbound, String username,
                           String movieIdHexString, String privacy){
    // get collections
    MongoCollection<Document> ratingCollection = getRatingCollection();
    MongoCollection<Document> userAssociatedRatingCollection = getUserAssociatedRatingCollection();
    MongoCollection<Document> movieCollection = getMovieCollection();

    // attempt to get the rating and the movie to see if they exist
    Document rating = ratingCollection.find(Filters.eq("name", ratingName)).first();
    Document movie = getMovieDocumentWithHexId(movieIdHexString);

    // if the movie exists
    if (movie != null) {
      // if the ratingCategory exists
      if (rating != null) {
        // if the upper bound matches
        if (rating.get("upperbound").equals(upperbound)) {
          // create new rating for ratingCategory
          Document newRating = new Document("userName", username).append("userRating", userRating)
                  .append("upperbound", upperbound).append("movieTitle", movie.get("movieTitle"))
                  .append("dateTimeCreated", new BsonDateTime(System.currentTimeMillis()))
                  .append("privacy", privacy);
          // add rating to the userRatings array within the rating
          Bson userRatingsUpdateOperation = Updates.push("userRatings", newRating);
          ratingCollection.updateOne(rating, userRatingsUpdateOperation);

          // add rating to UserAssociatedRating
          Document userAssocRating = userAssociatedRatingCollection.find(Filters.eq("username", username)).first();
          Bson userAssociatedRatingsUpdateOperation = Updates.push("username", newRating);
          userAssociatedRatingCollection.updateOne(userAssocRating, userAssociatedRatingsUpdateOperation);

          // if the movie does not have the rating category attached
          String ratingCategoryNames = (String) movie.get("ratingCategoryNames");
          if(!ratingCategoryNames.contains(ratingName)){
            // add the rating name to the rating category name within the movie
            Bson movieRatingCategoryUpdateOperation = Updates.push("ratingCategoryNames", ratingName);
            movieCollection.updateOne(movie, movieRatingCategoryUpdateOperation);
          }
        }
      }
      // if the ratingCategory does not exist
      else {
        // create new rating for ratingCategory
        Document newRating = new Document("userName", username).append("userRating", userRating)
                .append("upperbound", upperbound).append("movieTitle", movie.get("movieTitle"))
                .append("dateTimeCreated", new BsonDateTime(System.currentTimeMillis()))
                .append("privacy", privacy);
        // add rating to the userRatings array within the rating
        Bson userRatingsUpdateOperation = Updates.push("userRatings", newRating);
        ratingCollection.updateOne(rating, userRatingsUpdateOperation);

        // add rating to UserAssociatedRating
        Document userAssocRating = userAssociatedRatingCollection.find(Filters.eq("username", username)).first();
        Bson userAssociatedRatingsUpdateOperation = Updates.push("username", newRating);
        userAssociatedRatingCollection.updateOne(userAssocRating, userAssociatedRatingsUpdateOperation);

        // add rating category to ratingCategoryNames
        Bson movieRatingCategoryUpdateOperation = Updates.push("ratingCategoryNames", ratingName);
        movieCollection.updateOne(movie, movieRatingCategoryUpdateOperation);
      }
    }
  }


  /**
   * Creates and stores a review in the database. Reviews are the freeform text user generated data. Users are not
   * allowed to add a review for a movie that does not exist. Users are currently allowed to make multiple reviews
   * for the same movie.
   *
   * @param reviewDescription Freeform text from the user. No limits in size.
   * @param userName the user who created the review
   */
  public void createReview(String movieIdString, String reviewDescription, String userName, String privacy){
    // get collections
    MongoCollection<Document> reviewCollection = getReviewCollection();
    MongoCollection<Document> movieCollection = getMovieCollection();

    ObjectId movieId = new ObjectId(movieIdString);
    // get the movie object to make sure it exists
    Document movie = movieCollection.find(Filters.eq("_id", movieId)).first();

    // if the movie exists
    if(null != movie) {
      // get the current date time to attach to the new review
      BsonDateTime dateTimeCreated = new BsonDateTime(System.currentTimeMillis());
      // create a new review
      Document newReview = new Document("movieId", movieIdString).append("reviewDescription", reviewDescription)
              .append("userName", userName).append("dateTimeCreated", dateTimeCreated)
              .append("privacy", privacy);
      reviewCollection.insertOne(newReview);
    }
    // if the movie does not exist
    else{ }
  }

  /**
   * Creates and adds an actor to the database. Users cannot create an actor if the actor already exists by name.
   * This may need to be reconfigured to allow for two actors with the same name.
   *
   * @param actorName actor's name
   * @param dob the date of birth of the actor
   * @param movieTitle a movie that the actor appears in. More can be added later
   */
  public void createActor(String actorName, String dob, String movieTitle){
    // get collections
    MongoCollection<Document> actorCollection = getActorCollection();
    MongoCollection<Document> movieCollection = getMovieCollection();

    // TODO verify the actor does not already exist
    String actorId = "";
    // get the actor object to see if it exists
    Document actor = actorCollection.find(Filters.eq("id", actorId)).first();

    // if the actor exists
    if(null != actor) { }

    // if the actor does not exist
    else{
      // get the movie object to make sure it exists
      Document movie = movieCollection.find(Filters.eq("title", movieTitle)).first();
      // if the movie exists
      if(null != movie) {
        // create a new actor
        Document newReview = new Document("id", actorId).append("name", actorName)
                .append("dob", dob).append("movies", movieTitle);
        actorCollection.insertOne(newReview);

        // add actor to movie cast
        Bson movieUpdateOperation = Updates.push("principalCast", actorName);
        movieCollection.updateOne(movie, movieUpdateOperation);
      }
      // if the movie does not exist
      else{ }
    }
  }

  /**
   * Create and adds a movie to the database. Allows for the addition of two movies by the same name.
   *
   * @param movieTitle Title of movie. For example "Star Wars: Attack of the Clones" or "The Bee Movie"
   * @param director Director of the movie.
   * @param releaseDate Release date of the movie
   * @param runtime Movies runtime (in minutes?)
   * @param writers List of writers who worked on the movie
   * @param plotSummary Short description of the movie or its plot
   */
  public void createMovie(String movieTitle, String director, String releaseDate,
                          String runtime, String writers, String plotSummary){
    // get collections
    MongoCollection<Document> movieCollection = getMovieCollection();

    Document newMovie = new Document().append("title", movieTitle).append("director", director)
            .append("releaseDate", releaseDate).append("runtime", runtime).append("plotSummary", plotSummary)
            .append("movieImageId", getRandomImageId());
    movieCollection.insertOne(newMovie);
  }

  /**
   * <p>Get with filter operations allow for mutable searches within the database. These functions are called internally
   * by the <code>getXWithY</code> where X is a database entity and Y is a another database entity or field. </p>
   *
   * <p>For example the <code>getMoviesWithFilter()</code> method is used by the <code>getMoviesWithTag()</code>
   * method</p> to return all the movies that have the specified tag.
   */
    private static ArrayList<Movie> getMoviesWithFilter(MongoCollection<Document> moviesCollection, Bson filter) {
    var movies = moviesCollection.find(filter).map(document -> {
      var m = new Movie();
      m.setDirector(document.getString("director"));
      m.setRuntime(document.getString("runtime"));
      m.setSummary(document.getString("summary"));
      m.setTitle(document.getString("title"));
      m.setWriters(document.getString("writers"));
      m.setReleaseDate(document.getString("releaseDate"));
      m.setId(document.getObjectId("_id").toHexString());
      return m;
    });
    var list = new ArrayList<Movie>();
    movies.forEach(list::add);
    return list;
  }

  private static ArrayList<Actor> getActorsWithFilter(MongoCollection<Document> actorsCollection, Bson filter) {
    var actors = actorsCollection.find(filter).map(document -> {
      var a = new Actor();
      a.setName(document.getString("name"));
      a.setDateOfBirth(document.getString("dateOfBirth"));

      return a;
    });
    var list = new ArrayList<Actor>();
    actors.forEach(list::add);
    return list;
  }

  private static ArrayList<Review> getReviewsWithFilter(MongoCollection<Document> reviewsCollection, Bson filter) {
    var reviews = reviewsCollection.find(filter).map(document -> {
      var re = new Review();
      re.setReviewDescription(document.getString("reviewDescription"));
      re.setMovieId(document.getString("movieId"));
      re.setDateTimeCreated(document.get("dateTimeCreated").toString());
      re.setPrivacy(document.getString("privacy"));
      return re;
    });
    var list = new ArrayList<Review>();
    reviews.forEach(list::add);
    return list;
  }

  private static ArrayList<Rating> getRatingsWithFilter(MongoCollection<Document> ratingsCollection, Bson filter) {
    var ratings = ratingsCollection.find(filter).map(document -> {
      var ra = new Rating();
      ra.setRatingName(document.getString("ratingName"));
      ra.setUserRating(document.getString("userRating"));
      ra.setMovieTitle(document.getString("movieTitle"));
      ra.setDateTimeCreated(document.get("dateTimeCreated").toString());
      ra.setPrivacy(document.getString("privacy"));
      return ra;
    });
    var list = new ArrayList<Rating>();
    ratings.forEach(list::add);
    return list;
  }

  /**
   * get[DatabaseEntity]With[Parameter] methods are used to retrieve database entities by using another entity or a
   * given parameter. These make use of the get[DatabaseEntity]WithFilter methods.
   *
   */
  public List<Movie> getMoviesWithTag(String tag) {
    var moviesCollection = getMovieCollection();
    var filter = Filters.eq("tagNames", tag);
    return getMoviesWithFilter(moviesCollection, filter);
  }

  public List<Movie> getMoviesWithRatingCategory(String ratingCategory) {
    var moviesCollection = getMovieCollection();
    var filter = Filters.eq("ratingCategoryNames", ratingCategory);
    return getMoviesWithFilter(moviesCollection, filter);
  }

  public List<Movie> getMoviesWithActor(String actor) {
    var moviesCollection = getMovieCollection();
    var filter = Filters.eq("actorNames", actor);
    return getMoviesWithFilter(moviesCollection, filter);
  }

  public List<Movie> getMoviesWithTitle(String title) {
    var moviesCollection = getMovieCollection();
    var filter = Filters.eq("title", title);
    return getMoviesWithFilter(moviesCollection, filter);
  }

  public Document getMovieDocumentWithTitle(String title) {
    var movieCollections = getMovieCollection();
    var filter = Filters.eq("title", title);
    return movieCollections.find(filter).first();
  }

  /**
   * returns to X most recent movies based on the year of their release. This is done by sorting the ordering the
   * collection by date and returning the first X.
   */
  public List<Document> getRecentReleaseMovies() {
    // get the movie collection
    MongoCollection<Document> movieCollection = getMovieCollection();
    int numberToReturn = 10; // how many movies we want to get, currently 10
    // sort the entire collection
    List<Document> sortedList = (List<Document>) movieCollection.find().sort(descending("releaseDate"));
    // return the first numberToReturn movies
    return sortedList.subList(0,numberToReturn-1);
  }

  /**
   * retrieves movie using MongoDB unique hex identifier. Creates a ObjectID object to return the movie Document.
   * @param hexID String representation of the hex id.
   * @return Document of the movie matching the id, null if id is not found
   */
  public Document getMovieDocumentWithHexId(String hexID){
    MongoCollection<Document> movieCollection = getMovieCollection();
    ObjectId movieId = new ObjectId(hexID);
    return movieCollection.find(Filters.eq("_id", movieId)).first();
  }

  public List<Actor> getActorByName(String title) {
    var actorsCollection = getActorCollection();
    var filter = Filters.eq("title", title);
    return getActorsWithFilter(actorsCollection, filter);
  }

  public List<Rating> getUserAssociatedRatings(String userName) {
    var ratings = getRatingCollection();
    var filter = Filters.eq("user", userName);
    return getRatingsWithFilter(ratings, filter);
  }

  public List<Rating> getRatingsInRatingsCategory(String category) {
    var ratings = getRatingCollection();
    var filter = Filters.eq("category", category);
    return getRatingsWithFilter(ratings, filter);
  }

  public List<Review> getReviewsByMovieId(String movieId) {
    var reviews = getReviewCollection();
    var filter = Filters.eq("movieId", movieId);
    return getReviewsWithFilter(reviews, filter);
  }

  public List<Review> getReviewsByUser(String userName) {
    var reviews = getReviewCollection();
    var filter = Filters.eq("userName", userName);
    return getReviewsWithFilter(reviews, filter);
  }

  /**
   * Delete methods are used to remove entities from the database. These are usually referenced using their unique
   * MongoDB id. This helps prevent deleting movies that share names.
   */


  /**
   * Deletes the given tag from the given movie
   *
   * @param tagName name of tag to delete
   * @param movieId unique MongoDB id of the movie to delete the tag from
   * @param movieTitle title of movie to delete tag from
   */
  //remove a tag from a specific movie
  public void deleteTag(String tagName, String movieId, String movieTitle){
    //get tags and movie collections
    MongoCollection<Document> tagCollection = getTagCollection();
    MongoCollection<Document> movieCollection = getMovieCollection();

    //filters movie with the input movie ID
    Bson movieQuery = Filters.eq("id", movieId);
    Document movieWithId = movieCollection.find(movieQuery).first();
    if(movieWithId != null){
    //remove tag from movie with the corresponding ID
    Bson tagRemoveOp = Updates.pull(" tagNames", tagName);
    movieCollection.updateOne(movieWithId, tagRemoveOp);

    //find the tag needed to be deleted
    Bson titleQuery = Filters.eq("tagName", tagName);
    Document existingTag = tagCollection.find(titleQuery).first();
    //remove movie title from the tag
    Bson tagRemoveOP2 = Updates.pull("movieTitles", movieTitle);
    tagCollection.updateOne(existingTag, tagRemoveOP2);
  }
  else if(movieWithId == null){}
}

/**
 * Deletes every instance of a given tag
 *
 * @param tagName name of tag to delete
 */
public void deleteTags(String tagName){
  //get tags and movie collection
  MongoCollection<Document> tagCollection = getTagCollection();
  MongoCollection<Document> movieCollection = getMovieCollection();

  //Filters movies with tagName
  Bson tagQuery = Filters.eq("tagNames", tagName);
  MongoCursor<Document> movies = movieCollection.find(tagQuery).iterator();

  //iterate through each filtered movie and remove the tag name
  Bson tagRemoveOP = Updates.pull("tagNames", tagName);
  movies.forEachRemaining(document -> {
    tagCollection.updateOne(document, tagRemoveOP);
  });

  //set movieTitles array into an emptied one
  Bson removeAll = Updates.set("movieTitles", "");
  Document tag = tagCollection.find(Filters.eq("tagName", tagName)).first();
  tagCollection.updateOne(tag, removeAll);
}

/**
 * Deletes the provided movie
 *
 * @param movieTitle name of movie to delete
 * @param movieId unique MongoDB id of the movie to delete
 */
public void deleteMovie(String movieTitle, String movieId){
  //delete the movie's document
  //get collections
  MongoCollection<Document> movieCollection = getMovieCollection();
  MongoCollection<Document> actorCollection = getActorCollection();
  MongoCollection<Document> reviewCollection = getReviewCollection();
  MongoCollection<Document> tagCollection = getTagCollection();
  //delete the movie's document
  movieCollection.deleteOne(Filters.eq("id", movieId));
  //filters all actors with the listed movie
  MongoCursor<Document> actors = actorCollection.find(Filters.eq("movies", movieTitle)).iterator();
  Bson movieRemoval = Updates.pull("movies", movieTitle);
  actors.forEachRemaining(document -> {
    // Delete each movie correspond with movieTitle in each qualified actor
    actorCollection.updateOne(document, movieRemoval);
  });
  //delete movie within tags
  MongoCursor<Document> tags = tagCollection.find(Filters.eq("movieTitles", movieTitle)).iterator();
  Bson movieRemovalF = Updates.pull("movieTitles", movieTitle);
  tags.forEachRemaining(document -> {
    // Delete each movie correspond with movieTitle in each qualified actor
    tagCollection.updateOne(document, movieRemovalF);
  });

  //delete all reviews related to the movie
  reviewCollection.deleteMany(Filters.eq("movieTitle", movieTitle));
}

public void deleteActor(String id){
  MongoCollection<Document> actorCollection = getActorCollection();
  actorCollection.deleteOne(Filters.eq("id", id));
}

public void deleteReview(String title, String userName){
  MongoCollection<Document> reviewCollection = getReviewCollection();
  //get all reviews which has the required movie title and userName
  Bson reviewFilter = Filters.and(Filters.eq("movieTitle", title), Filters.eq("userName", userName));
  reviewCollection.deleteMany(reviewFilter);
}


}
