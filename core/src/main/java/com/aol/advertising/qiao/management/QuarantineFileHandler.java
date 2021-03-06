/****************************************************************************
 * AOL CONFIDENTIAL INFORMATION
 *
 * Copyright (c) 2014 AOL Inc.  All Rights Reserved.
 * Unauthorized reproduction, transmission, or distribution of
 * this software is a violation of applicable laws.
 *
 ****************************************************************************
 * Department:  AOL Advertising
 *
 * File Name:   QuarantineFileHandler.java	
 * Description:
 * @author:     ytung
 *
 ****************************************************************************/

package com.aol.advertising.qiao.management;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aol.advertising.qiao.exception.ConfigurationException;
import com.aol.advertising.qiao.injector.file.DoneFileHandler.TargetNamingStrategy;

/**
 * QuarantineFileHandler moves a "quarantined" file to target directory. A
 * "quarantined" file is a file that has been identified as it exhibits some
 * integrity issues by injector.
 */
public class QuarantineFileHandler
{

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private String targetDir;
    private String srcDir;
    private TargetNamingStrategy targetNamingStrategy;
    private String timeSuffixFormat = "%1$tY%1$tm%1$tdT%1$tH%1$tM%1$tS";
    private String staticSuffix;

    private Path targetDirPath;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);


    public void init()
    {
        if (isInitialized.compareAndSet(false, true))
        {
            _validate();

            if (targetNamingStrategy == null)
                targetNamingStrategy = TargetNamingStrategy.APPEND_SOURCE_MODTIME;

            targetDirPath = FileSystems.getDefault().getPath(targetDir);
        }
    }


    private void _validate()
    {

        if (targetDir == null)
            throw new ConfigurationException("targetDir not defined");

        if (srcDir == null)
            throw new ConfigurationException("srcDir not defined");

        if (targetNamingStrategy == TargetNamingStrategy.APPEND_STATIC_SUFFIX
                && staticSuffix == null)
            throw new ConfigurationException(
                    "targetNamingStrategy=APPEND_STATIC_SUFFIX but staticSuffix not defined");

    }


    private boolean nameContainsChecksum(Path filePath, long checksum)
    {
        String name = filePath.getFileName().toString();
        String cksum = String.format(".%d", checksum);
        if (name.contains(cksum))
            return true;

        return false;
    }


    public void moveFileToQuarantineDir(Path file, long checksum)
            throws IOException
    {
        Path target_path = resolveTargetPath(file, checksum);
        logger.info("Move file from " + file + " to " + target_path);
        Files.move(file, target_path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }


    public void moveFileToQuarantineDirIfExists(Path filePath, long checksum)
    {
        if (Files.notExists(filePath))
            return;

        try
        {
            moveFileToQuarantineDir(filePath, checksum);
        }
        catch (IOException e)
        {
            // file manager can move the file before Files.move to happen            
            logger.info("file " + filePath.toString()
                    + " not exist - probably already moved");
        }

    }


    private Path resolveTargetPath(Path sourceFilePath, long checksum)
            throws IOException
    {
        String prefix_name = sourceFilePath.getFileName().toString();
        if (!nameContainsChecksum(sourceFilePath, checksum))
            prefix_name += ("." + checksum);
        String target_fname = fixupTargetName(sourceFilePath, prefix_name);

        return targetDirPath.resolve(target_fname);
    }


    // sourceFilePath must contain full path dir info
    private String fixupTargetName(Path sourceFilePath, String prefix)
            throws IOException
    {
        String target_fname;
        switch (targetNamingStrategy)
        {
            case APPEND_SOURCE_MODTIME:
            {
                long mod_time = Files.getLastModifiedTime(sourceFilePath)
                        .toMillis();
                String suffix = String.format(timeSuffixFormat, mod_time);
                target_fname = prefix + "." + suffix;

                break;
            }
            case USE_SOURCE_NAME:
            {
                target_fname = prefix;
                break;
            }
            case APPEND_SYSTEM_TIME:
            {
                long now = System.currentTimeMillis();
                String suffix = String.format(timeSuffixFormat, now);
                target_fname = prefix + "." + suffix;
                break;
            }
            case APPEND_STATIC_SUFFIX:
            {
                target_fname = prefix + staticSuffix;
                break;
            }
            default:
            {
                logger.warn("not supported strategy: " + targetNamingStrategy
                        + ". set to USE_SOURCE_NAME");
                target_fname = prefix;
            }
        }

        return target_fname;
    }


    public Path renameFileToIncludeChecksum(Path file, long checksum)
            throws IOException
    {
        Path new_path = resolveNewFilePath(file, checksum);
        if (!new_path.equals(file))
        {
            logger.info("Rename file from " + file + " to " + new_path);
            Files.move(file, new_path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }

        return new_path;
    }


    private Path resolveNewFilePath(Path sourceFilePath, long checksum)
    {
        String fname = sourceFilePath.getFileName().toString() + "." + checksum;
        return sourceFilePath.resolveSibling(fname);
    }


    public void setTargetDir(String targetDir)
    {
        this.targetDir = targetDir;
    }


    public void setTargetNamingStrategy(
            TargetNamingStrategy targetNamingStrategy)
    {
        this.targetNamingStrategy = targetNamingStrategy;
    }


    public void setTimeSuffixFormat(String timeSuffixFormat)
    {
        this.timeSuffixFormat = timeSuffixFormat;
    }


    public void setStaticSuffix(String staticSuffix)
    {
        this.staticSuffix = staticSuffix;
    }


    public void setSrcDir(String srcDir)
    {
        this.srcDir = srcDir;
    }

}
