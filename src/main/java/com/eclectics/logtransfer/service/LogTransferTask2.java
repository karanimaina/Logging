package com.eclectics.logtransfer.service;


import com.eclectics.logtransfer.config.DirectoryMappingConfig;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class LogTransferTask2 {
@Value ("${target.username}")
String targetUsername;
@Value ("${target.password}")
String targetPassword;
@Value ("${target.targetIp}")
String targetIp;
@Value ("${target.targetPort}")
int targetPort;
private final DirectoryMappingConfig directoryMappingConfig;

@Scheduled (cron = "0 * * * * *")
public void transferLogs() {
	List<String> directoriesList = directoryMappingConfig.getDirectories();
	log.info("Configuration loaded: " + directoryMappingConfig.getDirectories());
	Map<String, String> directoryMapping = new HashMap<>();
	for (String directory : directoriesList) {
		for (Map.Entry<String, String> entry : parseDirectories(directory).entrySet()) {
			directoryMapping.put(entry.getKey(), entry.getValue());
		}
	}
	log.info("directoryMapping: " + directoryMapping);
	for (Map.Entry<String, String> entry : directoryMapping.entrySet()) {
		String sourceFolder = entry.getKey();
		String targetFolder = entry.getValue();
		log.info("Original source folder: " + sourceFolder);
		// Normalize and log the source path
		Path normalizedSourcePath = Paths.get(sourceFolder).toAbsolutePath();
		sourceFolder = normalizedSourcePath.toString();
		log.info("Normalized source folder: " + sourceFolder);
		File folder = new File(sourceFolder);
		log.info("folder: " + folder);
		// Check if source directory exists
		if (!folder.exists() || !folder.isDirectory()) {
			log.error("Source folder does not exist or is not a directory: " + sourceFolder);
			continue;
		}
		log.info("Source folder verified: " + folder.getAbsolutePath());
		log.info("Target folder: " + targetFolder);
		final int maxRetries = 10;
		int attempt = 0;
		boolean success = false;
		
		while (attempt < maxRetries && !success) {
			attempt++;
			log.info("Attempt " + attempt + " to connect to SFTP server");
			try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(targetUsername, targetIp, targetPort);
			session.setPassword(targetPassword);
			
			Properties config = new Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			
			session.connect(600000);
			
			ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();
			
			// Collect .gz files from all subdirectories
			List<File> files = collectGzFiles(folder);
			log.info("Collecting .gz files : size: " + files.size());
			for (File file : files) {
				// Determine the relative path to maintain the folder structure
				String relativePath = file.getAbsolutePath().substring(sourceFolder.length() + 1);
				String targetDirPath = Paths.get(targetFolder, relativePath).getParent().toString();
				String targetFilePath = Paths.get(targetDirPath, file.getName()).toString();
				
				log.info("relativePath: " + relativePath);
				log.info("targetDirPath: " + targetDirPath);
				log.info("targetFilePath: " + targetFilePath);
				
				// Create the necessary target directories
				createDirectories(channelSftp, targetDirPath);
				
				log.info("Processing file: " + file.getAbsolutePath());
				channelSftp.put(file.getAbsolutePath(), targetFilePath);
				log.info("Transferred: " + file.getName());
				if (file.delete()) {
					log.info("Deleted: " + file.getName());
				} else {
					log.warn("Failed to delete: " + file.getName());
				}
			}
			
			if (files.isEmpty()) {
				log.warn("No files found in the source folder.");
			}
			
			channelSftp.disconnect();
			session.disconnect();
			log.info("File transfer from " + sourceFolder + " to " + targetFolder + " successful.");
		} catch (SftpException e) {
			log.error("SFTP error during file transfer: " + e.getMessage(), e);
			if (e.id == ChannelSftp.SSH_FX_PERMISSION_DENIED) {
				log.error("Permission denied: Check your SFTP user permissions.");
			}
		} catch (Exception e) {
			log.error("File transfer from " + sourceFolder + " to " + targetFolder + " failed.", e);
		}
	}
}}

private List<File> collectGzFiles(File folder) {
	List<File> gzFiles = new ArrayList<>();
	File[] files = folder.listFiles();
	if (files != null) {
		for (File file : files) {
			if (file.isDirectory()) {
				gzFiles.addAll(collectGzFiles(file));
			} else if (file.isFile() && file.getName().endsWith(".gz")) {
				gzFiles.add(file);
			}
		}
	}
	return gzFiles;
}

private void createDirectories(ChannelSftp channelSftp, String targetFolder) throws SftpException {
	String[] folders = targetFolder.split("/");
	StringBuilder path = new StringBuilder();
	for (String folder : folders) {
		if (folder.isEmpty()) continue; // Skip empty segments
		path.append("/").append(folder);
		String replacedPath = path.toString().replaceAll("\\\\", "/");
		try {
			channelSftp.cd(replacedPath.trim());
		} catch (SftpException e) {
			if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
				try {
					log.info("Creating directory: " + replacedPath);
					channelSftp.mkdir(replacedPath.trim());
					channelSftp.cd(replacedPath.trim());
					log.info("Created directory: " + replacedPath);
				} catch (SftpException mkdirException) {
					log.error("Failed to create directory: " + replacedPath, mkdirException);
					throw mkdirException; // Rethrow the exception after logging
				}
			} else {
				log.error("Failed to change directory: " + replacedPath, e);
				throw e; // Rethrow the exception after logging
			}
		}
	}
}

private Map<String, String> parseDirectories(String directoriesString) {
	Map<String, String> directories = new HashMap<>();
	String[] keyValue = directoriesString.split(":");
	if (keyValue.length == 2) {
		String key = keyValue[0].trim().replace("\"", "").replace("\\\\", "\\");
		String value = keyValue[1].trim().replace("\"", "");
		directories.put(key, value);
	}
	
	return directories;
}}