package email.wf;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;
import email.wf.util.ProcessHelper;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

public class ExtractAttachmentLambda {
    private Logger logger = LoggerFactory.getLogger(ExtractAttachmentLambda.class);

    private static AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
    private static ExtractAttachments extractor = new ExtractAttachments(s3);
    private static final String ATTACHMENT_BUCKET = System.getenv("ATTACHMENT_BUCKET");
    private static final String MULTIPART = "multipart";
    private static final String JPG = "jpg";
    private static final String JPEG = "jpeg";

    public void handle(S3Event event, Context context) {
       logger.info("invoking ExtractAttachmentLambda.handle V8");

        Map<String, S3ObjectInputStream> keyedStream = ProcessHelper.buildProcessMap(s3, event);
        keyedStream.entrySet().parallelStream().forEach((e) -> extractor.apply(e.getKey(), e.getValue()));
    }

    private static class ExtractAttachments implements BiFunction<String, S3ObjectInputStream, Void> {
        private static  Logger logger = LoggerFactory.getLogger(ExtractAttachments.class);

        private AmazonS3 s3;
        public ExtractAttachments(AmazonS3 s3) {
            this.s3 = s3;
        }

        @Override
        public Void apply(String key, S3ObjectInputStream value) {

            try {
                MimeMessage message = new MimeMessage(null, value);
                logger.info("Subject: [{}]", message.getSubject());

                if (message.getContentType().contains(MULTIPART)) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    Multipart part = (Multipart) message.getContent();

                    // loop through all attachments
                    for(int i = 0; i < part.getCount(); i++) {
                        logger.info("count = [{}]", i);
                        extractAndSaveJpegImage(random, part.getBodyPart(i));
                    }
                }
            } catch (Exception ex) {
                logger.error("failed: {}", ex);
            }

            return null;
        }

        private void extractAndSaveJpegImage(ThreadLocalRandom random, BodyPart bodyPart) throws Exception {
            String filename = bodyPart.getFileName();
            if(isAttachment(bodyPart) && (filename != null && isJpegImage(FilenameUtils.getExtension((filename))))) {
                // get the jpg content
                byte[] attachment = IOUtils.toByteArray((InputStream) bodyPart.getContent());
                int size = attachment.length;

                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentLength(size);

                logger.info("Filename = [{}], size = [{}]", filename, size);
                s3.putObject(
                    new PutObjectRequest(ATTACHMENT_BUCKET, random.nextInt(0, 1001) + "_" + filename.replaceAll(" ", "_") , new ByteArrayInputStream(attachment), meta));
            }
        }

        private boolean isAttachment(BodyPart bodyPart ) throws Exception {
            String disposition = bodyPart.getDisposition();
            return Part.ATTACHMENT.equalsIgnoreCase(disposition) || Part.INLINE.equalsIgnoreCase(disposition);
        }

        private boolean isJpegImage(String extenstion) {
            return JPEG.equalsIgnoreCase(extenstion) || JPG.equalsIgnoreCase(extenstion);
        }
    }
}
