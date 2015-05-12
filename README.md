Caboclo (Client API for Backup on Cloud) is a common API that allows backup and restore of a collection of objects as a single entity through different cloud storage services. The API is written in Java, and the REST version of the API allows programs written in different languages to create backups in several cloud providers (e.g., Amazon S3, Dropbox, Google Drive). By using Caboclo, developers can use various storage services through a unified API simplifying the development task.

The API implements a unique control engine to manage the backup/restore operations despite the different approaches adopted by each individual cloud storage service.

A user program performs REST calls to Caboclo selecting the desired operation (e.g., data backup or restore) and the cloud environment (e.g., Amazon S3). Next, the user data is transferred from/to the cloud service. The supported cloud infrastructures are:

-> Amazon S3
-> OpenStack
-> Dropbox
-> Microsoft OneDrive
-> Google Drive

Please, refer to the Wiki Section:  https://github.com/modcs/caboclo/wiki
