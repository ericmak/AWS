# AWS
AWS related development

ProcessEmail is a prototype that shows how to extract JPEG attachments in an email and convert the image into PNG format.

It contains AWS Lambda Java classes that:
1. read email from a mail account hosted by Amazon Simple Email Service and save that email into a S3 bucket
2. extract the JPEG image attachments in email and save image into a second S3 bucket
3. the JPEG image is converted into PNG format and save the converted image into a third S3 bucket
