package com.blitline.image.spring;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.blitline.image.BlitlineImageJob;
import com.blitline.image.BlitlinePostResults;
import com.blitline.image.spring.postback.BlitlinePostbackUrlProvider;

/**
 * This class is a service facade for the Blitline image-processing service intended primarily for use with the Spring Framework,
 * but usable with manual configuration as well. This class requires at least the client's Blitline application ID (API key) to be
 * set in order to create job specifications and will automatically apply a postback URL for job completion notification if present.
 * It can also optionally remember an S3 source bucket and hold a Spring {@link RestTemplate} configured for compatibility with
 * Blitline's API.
 *
 * The Java interface for this class has been designed to work with but has not been tested on Spring for Android's
 * {@code RestTemplate} implementation. The serialization formatting applied to conform to Blitline's syntax is configured using
 * Jackson annotations and is not expected to work as-is with Gson.
 *
 * A typical use of this facade after setting the {@code applicationId}, {@code s3bucket}, and optionally {@code postbackUrl} would
 * be:
 *
 * <pre>
 * BlitlineImageJob j = bis.loadS3Key(&quot;sourceimg.jpg&quot;).apply(
 *     Blitline.resizeToFit(512, 384).andSaveResultTo(
 *         SavedImage.withId(&quot;abcd1234.color&quot;).toS3(&quot;destination-bucket&quot;, &quot;dest-color.jpg&quot;)
 *         ).thenApply(
 *             Blitline.toGrayScale().andSaveResultTo(
 *                 SavedImage.withId(&quot;abcd1234.gray&quot;).toS3(&quot;destination-bucket&quot;, &quot;dest-gray.jpg&quot;)
 *                 )));
 *
 * ResponseEntity&lt;BlitlinePostResults&gt; resp = bis.submitJob(j);
 * </pre>
 *
 * @author Christopher Smith
 *
 */
@Service
public class BlitlineImageService {

    private String applicationId;

    public String getApplicationId() {
        return applicationId;
    }

    @Value("${blitline.applicationId:no application ID set!}")
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    private BlitlinePostbackUrlProvider postbackUrlProvider;

    public BlitlinePostbackUrlProvider getPostbackUrlProvider() {
        return postbackUrlProvider;
    }

    @Autowired(required = false)
    public void setPostbackUrlProvider(BlitlinePostbackUrlProvider postbackUrlProvider) {
        this.postbackUrlProvider = postbackUrlProvider;
    }

    private String s3bucket;

    public String getS3bucket() {
        return s3bucket;
    }

    @Value("${blitline.s3sourceBucket:#{null}}")
    public void setS3bucket(String s3bucket) {
        this.s3bucket = s3bucket;
    }

    private boolean alwaysExtendedMetadata = false;

    public boolean isAlwaysExtendedMetadata() {
        return alwaysExtendedMetadata;
    }

    /**
     * If this flag is set, this service instance will always request extended image metadata from Blitline.
     *
     * @param alwaysExtendedMetadata
     *            whether to request extended metadata on all jobs
     */
    public void setAlwaysExtendedMetadata(boolean alwaysExtendedMetadata) {
        this.alwaysExtendedMetadata = alwaysExtendedMetadata;
    }

    public static final URI BLITLINE_SUBMIT_POST_URI = URI.create("http://api.blitline.com/job");

    private RestTemplate blitlineRest;

    public RestTemplate getBlitlineRest() {
        return blitlineRest;
    }

    @Autowired(required = false)
    @BlitlineApi
    public void setBlitlineRest(RestTemplate blitlineRest) {
        this.blitlineRest = blitlineRest;
    }

    public ResponseEntity<BlitlinePostResults> submitJob(BlitlineImageJob job) {
        return blitlineRest.postForEntity(BLITLINE_SUBMIT_POST_URI, job, BlitlinePostResults.class);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BlitlineImageService[applicationId=").append(applicationId);
        if (postbackUrlProvider != null)
            sb.append(",postbackUrlProvider=").append(postbackUrlProvider);
        if (s3bucket != null)
            sb.append(",s3Bucket=").append(s3bucket);
        sb.append(']');
        return sb.toString();
    }

    /**
     * Creates a job builder with the application ID set, along with the completion postback URL if specified. The returned builder
     * does not have a source location specified.
     *
     * @return a job builder with application ID and optionally postback URL set
     */
    public BlitlineImageJob.Builder jobBuilder() {
        BlitlineImageJob.Builder builder = BlitlineImageJob.forApplication(applicationId);
        if (postbackUrlProvider != null)
            builder.withPostback(postbackUrlProvider.getPostbackUrl());

        if (alwaysExtendedMetadata)
            builder.withExtendedMetadata();

        return builder;
    }

    /**
     * Creates a job builder with the application ID and source URL set, along with the completion postback URL if specified.
     *
     * @param src
     *            the URL of the source image to be loaded
     * @return a job builder with application ID and optionally postback URL set
     */
    public BlitlineImageJob.Builder loadUrl(String src) {
        return jobBuilder().fromUrl(src);
    }

    /**
     * Creates a job builder with the application ID and source URL set, along with the completion postback URL if specified.
     *
     * @param src
     *            the URL of the source image to be loaded
     * @return a job builder with application ID and optionally postback URL set
     */
    public BlitlineImageJob.Builder loadUrl(URI src) {
        return jobBuilder().fromUrl(src);
    }

    /**
     * Creates a job builder with the application ID set, along with the completion postback URL if specified, that will load the
     * specified object from the configured S3 source bucket.
     *
     * @param key
     *            the key of the S3 object to be loaded from the configured source bucket as the source image
     * @return a job builder with application ID and optionally postback URL set
     */
    public BlitlineImageJob.Builder loadS3Key(String key) {
        return jobBuilder().fromS3(s3bucket, key);
    }

    /**
     * Creates a job builder with the application ID set, along with the completion postback URL if specified, that will load the
     * specified object from S3.
     *
     * @param bucket
     *            the S3 bucket to load the source image from
     * @param key
     *            the key of the S3 object to be loaded from the specified source bucket as the source image
     * @return a job builder with application ID and optionally postback URL set
     */
    public BlitlineImageJob.Builder loadS3Object(String bucket, String key) {
        return jobBuilder().fromS3(bucket, key);
    }
}
