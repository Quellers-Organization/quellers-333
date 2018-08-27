package org.elasticsearch.protocol.xpack.ml;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Request object to get {@link org.elasticsearch.protocol.xpack.ml.job.stats.JobStats} objects with the matching `jobId`s
 *
 * `_all` explicitly gets all the jobs in the cluster
 * An empty request (no `jobId`s) implicitly gets all the jobs in the cluster
 */
public class GetJobsStatsRequest extends ActionRequest implements ToXContentObject {

    public static final ParseField JOB_ID = new ParseField("job_id");
    public static final ParseField ALLOW_NO_JOBS = new ParseField("allow_no_jobs");

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<GetJobsStatsRequest, Void> PARSER = new ConstructingObjectParser<>(
        "get_jobs_stats_request",
        true, a -> new GetJobsStatsRequest((List<String>) a[0]));

    static {
        PARSER.declareField(ConstructingObjectParser.constructorArg(),
            p -> Arrays.asList(Strings.commaDelimitedListToStringArray(p.text())),
            JOB_ID, ObjectParser.ValueType.STRING_ARRAY);
        PARSER.declareBoolean(GetJobsStatsRequest::setAllowNoJobs, ALLOW_NO_JOBS);
    }

    private static final String ALL_JOBS = "_all";

    private final List<String> jobIds;
    private Boolean allowNoJobs;

    /**
     * Explicitly gets all jobs statistics
     *
     * @return a {@link GetJobsStatsRequest} for all existing jobs
     */
    public static GetJobsStatsRequest allJobsStats(){
        return new GetJobsStatsRequest(ALL_JOBS);
    }

    GetJobsStatsRequest(List<String> jobIds) {
        if (jobIds.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("jobIds must not contain null values");
        }
        this.jobIds = new ArrayList<>(jobIds);
    }

    /**
     * Get the specified Job's statistics via their unique jobIds
     *
     * @param jobIds must be non-null and each jobId must be non-null
     */
    public GetJobsStatsRequest(String... jobIds) {
        this(Arrays.asList(jobIds));
    }

    /**
     * All the jobIds for which to get statistics
     */
    public List<String> getJobIds() {
        return jobIds;
    }

    public Boolean isAllowNoJobs() {
        return this.allowNoJobs;
    }

    /**
     * Whether to ignore if a wildcard expression matches no jobs.
     *
     * This includes `_all` string or when no jobs have been specified
     *
     * @param allowNoJobs When {@code true} ignore if wildcard or `_all` matches no jobs. Defaults to {@code true}
     */
    public void setAllowNoJobs(boolean allowNoJobs) {
        this.allowNoJobs = allowNoJobs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobIds, allowNoJobs);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        GetJobsStatsRequest that = (GetJobsStatsRequest) other;
        return Objects.equals(jobIds, that.jobIds) &&
            Objects.equals(allowNoJobs, that.allowNoJobs);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(JOB_ID.getPreferredName(), Strings.collectionToCommaDelimitedString(jobIds));
        if (allowNoJobs != null) {
            builder.field(ALLOW_NO_JOBS.getPreferredName(), allowNoJobs);
        }
        builder.endObject();
        return builder;
    }
}
