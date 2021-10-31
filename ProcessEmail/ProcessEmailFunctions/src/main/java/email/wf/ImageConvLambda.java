package email.wf;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import email.wf.util.ProcessHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.function.BiFunction;

public class ImageConvLambda {
    private Logger logger = LoggerFactory.getLogger(ImageConvLambda.class);

    private static final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
    private static ConvertJpg2Png converter  = new ConvertJpg2Png(s3);

    private static final String RESULT_BUCKET = System.getenv("RESULT_BUCKET");

    public void handle(S3Event event, Context context) {
        // S3Event triggered by ATTACHMENT_BUCKET
        // write PNG image to RESULT_BUCKET
        logger.info("invoking ImageConvLambda.handler V5");
        Map<String, S3ObjectInputStream> keyedStream = ProcessHelper.buildProcessMap(s3, event);
        keyedStream.entrySet().parallelStream().forEach(e -> converter.apply(e.getKey(), e.getValue()));
   }

   private static class ConvertJpg2Png implements BiFunction<String, S3ObjectInputStream, Void> {
        private Logger logger = LoggerFactory.getLogger(ConvertJpg2Png.class);

        private AmazonS3 s3;
        public ConvertJpg2Png(AmazonS3 s3) {
            this.s3 = s3;
        }

        @Override
       public Void apply(String key, S3ObjectInputStream value) {
            try {
                // write converted image to this ByteArrayOutputStream from original image
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BufferedImage inputImage = ImageIO.read(value);
                ImageIO.write(inputImage, "PNG", baos);

                byte[] buffer = baos.toByteArray();
                // channel the converted image to this InputStream for writing to S3
                InputStream convertedImage = new ByteArrayInputStream(buffer);

                int length = buffer.length;
                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentLength(length);

                logger.info("length: [{}]", length);
                s3.putObject(new PutObjectRequest(RESULT_BUCKET, key + ".png", convertedImage, meta));

            } catch (Exception ex) {
                logger.error("Failed to convert image. Cause: [{}]", ex);
            }
            return null;
        }
   }
}
