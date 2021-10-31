package email.wf.util;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class ProcessHelper {
     public static Map<String, S3ObjectInputStream> buildProcessMap(AmazonS3 s3, S3Event event) {
        return event.getRecords().parallelStream().collect(
            toMap(record ->  record.getS3().getObject().getKey(),
                record -> s3
                    .getObject(record.getS3().getBucket().getName(), record.getS3().getObject().getKey())
                    .getObjectContent()
            ));
    }
}
