package net.lshift.diffa.railyard;

import com.google.inject.Inject;
import net.lshift.diffa.adapter.scanning.AggregationBuilder;
import net.lshift.diffa.adapter.scanning.HttpRequestParameters;
import net.lshift.diffa.adapter.scanning.SliceSizeParser;
import net.lshift.diffa.config.CategoryDescriptor;
import net.lshift.diffa.railyard.questions.QuestionBuilder;
import net.lshift.diffa.railyard.plumbing.RestEasyRequestWrapper;
import net.lshift.diffa.system.Endpoint;
import net.lshift.diffa.system.SystemConfiguration;
import net.lshift.diffa.versioning.VersionStore;
import org.jboss.resteasy.spi.HttpRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.util.Map;

@Path("/{space}/interview")
public class InterviewResource {

  private VersionStore versionStore;
  private SystemConfiguration systemConfig;

  @Inject
  public InterviewResource(VersionStore versionStore, SystemConfiguration systemConfig) {
    this.versionStore = versionStore;
    this.systemConfig = systemConfig;
  }

  @GET
  @Path("/{endpoint}")
  @Produces("application/json")
  public Question getNextQuestion(@PathParam("space") String space, @PathParam("endpoint") String endpointName) {

    Endpoint endpoint = systemConfig.getEndpoint(space, endpointName);
    Map<String,CategoryDescriptor> categories = endpoint.getCategories();

    return QuestionBuilder.buildInitialQuestion(categories);
  }

  @POST
  @Path("/{endpoint}")
  @Produces("application/json")
  public Question getNextQuestion(@PathParam("space") String space, @PathParam("endpoint") String endpoint,
                                  @Context final HttpRequest request) throws IOException {

    HttpRequestParameters parameters = new RestEasyRequestWrapper(request);
    SliceSizeParser sliceSizeParser = new SliceSizeParser(parameters);

    AggregationBuilder aggregationBuilder = new AggregationBuilder(parameters);
    //aggregationBuilder.toSet()


    //versionStore.continueInterview()

    return new NoFurtherQuestions();
  }
}