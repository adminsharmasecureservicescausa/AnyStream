INSERT INTO mediaReferences(id,
                            gid,
                            contentGid,
                            rootContentGid,
                            addedAt,
                            addedByUserId,
                            mediaKind,
                            type,
                            updatedAt,
                            filePath,
                            directory,
                            hash,
                            fileIndex)
VALUES (NULL,
        :ref.gid,
        :ref.contentGid,
        :ref.rootContentGid,
        :ref.addedAt,
        :ref.addedByUserId,
        :ref.mediaKind,
        :ref.type,
        :ref.updatedAt,
        :ref.filePath,
        :ref.directory,
        :ref.hash,
        :ref.fileIndex)