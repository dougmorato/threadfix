////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2014 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.service.repository;

import com.denimgroup.threadfix.data.entities.Application;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.errors.EncryptionException;

import java.io.File;
import java.io.IOException;

public class GitService {
	
	private GitService() {
		// intentionally inaccessible
	}
	
	public static void main(String[] args) throws NoWorkTreeException, IOException {
		File test = new File("C:\\test\\scratch\\13\\bodgeit");
        File clinictest = new File("C:\\test\\scratch\\13\\clinic");
        File tftest = new File("C:\\test\\scratch\\13\\tf");
        String authenURL = "http://satgit2.denimgroup.com/sbir/bodgeit.git";
        String nonauthenURL = "https://github.com/spring-projects/spring-petclinic.git";
        String tfURL = "https://github.com/denimgroup/threadfix.git";
        try {
            File gitDirectoryFile = new File("C:\\test\\scratch\\13\\bodgeit\\.git");
                Repository localRepo = new FileRepository(gitDirectoryFile);
                Git git = new Git(localRepo);
            git.fetch().setCredentialsProvider(new UsernamePasswordCredentialsProvider("aaa", "aaaa")).call();

        } catch (GitAPIException e) {
            e.printStackTrace();
        }
//		new FileRepository(test).getWorkTree();
	}

	// Cursory testing indicates that this works.
	public static Repository cloneGitTreeToDirectory(Application application, File fileLocation) {
		
		if (fileLocation.exists()) {
			try {
				File gitDirectoryFile = new File(fileLocation.getAbsolutePath() + File.separator + ".git");
				if (!gitDirectoryFile.exists()) {
                    Git newRepo = clone(application, fileLocation);
                    if (newRepo != null)
                        return newRepo.getRepository();
				} else {
                    Repository localRepo = new FileRepository(gitDirectoryFile);
                    Git git = new Git(localRepo);
//                    // Fetch repository if user asked for new revision/branch
//                    if (application.getRepositoryBranch() != null
//                            && !application.equals(application.getRepositoryDBBranch())) {
//                        application.setRepositoryDBBranch(application.getRepositoryBranch());
//                            git = fetch(application, git);
//                    }
					return git.getRepository();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JGitInternalException e) {
				e.printStackTrace();
            }
		} else {
			try {
                Git result = clone(application, fileLocation);
				if (result != null) {
					return result.getRepository();
				}
			} catch (JGitInternalException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

    private static Git clone(Application application, File fileLocation) {
        Git git = null;
        try {
            CloneCommand clone = Git.cloneRepository();
            clone.setURI(application.getRepositoryUrl())
                    .setDirectory(fileLocation);
            if (application.getRepositoryEncryptedUserName() != null
                    && application.getRepositoryEncryptedPassword() != null) {
                decryptRepositoryCredentials(application);
                UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(application.getRepositoryUserName(),
                        application.getRepositoryPassword());
                clone.setCredentialsProvider(credentials);
            }

            if (application.getRepositoryBranch() != null) {
                application.setRepositoryDBBranch(application.getRepositoryBranch());
                clone.call()
                        .checkout()
                        .setCreateBranch(true)
                        .setName(application.getRepositoryBranch())
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint(application.getRepositoryBranch()).call();
            } else {
                git = clone.call();
            }
        } catch (WrongRepositoryStateException e) {
            e.printStackTrace();
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        } catch (DetachedHeadException e) {
            e.printStackTrace();
        } catch (InvalidRemoteException e) {
            e.printStackTrace();
        } catch (CanceledException e) {
            e.printStackTrace();
        } catch (RefNotFoundException e) {
            e.printStackTrace();
        } catch (NoHeadException e) {
            e.printStackTrace();
        } catch (TransportException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return git;

    }


//    private static Git fetch(Application application, Git git) {
//        try {
//            if (application.getRepositoryEncryptedUserName() != null
//                    && application.getRepositoryEncryptedPassword() != null) {
//                decryptRepositoryCredentials(application);
//                UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(application.getRepositoryUserName(),
//                        application.getRepositoryPassword());
//                git.fetch().setCredentialsProvider(credentials).call();
//            } else {
//                git.fetch().call();
//            }
//        } catch (WrongRepositoryStateException e) {
//            e.printStackTrace();
//        } catch (InvalidConfigurationException e) {
//            e.printStackTrace();
//        } catch (DetachedHeadException e) {
//            e.printStackTrace();
//        } catch (InvalidRemoteException e) {
//            e.printStackTrace();
//        } catch (CanceledException e) {
//            e.printStackTrace();
//        } catch (RefNotFoundException e) {
//            e.printStackTrace();
//        } catch (NoHeadException e) {
//            e.printStackTrace();
//        } catch (TransportException e) {
//            e.printStackTrace();
//        } catch (GitAPIException e) {
//            e.printStackTrace();
//        }
//        return git;
//
//    }

    private static Application decryptRepositoryCredentials(Application application) {
        try {
            if (application != null && application.getRepositoryEncryptedPassword() != null &&
                    application.getRepositoryEncryptedUserName() != null) {
                application.setRepositoryPassword(ESAPI.encryptor().decrypt(application.getRepositoryEncryptedPassword()));
                application.setRepositoryUserName(ESAPI.encryptor().decrypt(application.getRepositoryEncryptedUserName()));
            }
        } catch (EncryptionException e) {
            e.printStackTrace();
        }
        return application;
    }

    // TODO move this somewhere central
    private static final String baseDirectory = "scratch/";

    // TODO move to some sort of repository manager instead of tying to the Git implementation.
    public static File getWorkTree(Application application) {

        File applicationDirectory = new File(baseDirectory + application.getId());

        if (application.getRepositoryUrl() != null && !application.getRepositoryUrl().trim().isEmpty()) {
            Repository repo = GitService.cloneGitTreeToDirectory(application, applicationDirectory);

            if (repo != null && repo.getWorkTree() != null && repo.getWorkTree().exists()) {
                return repo.getWorkTree();
            } else {
                return applicationDirectory;
            }
        } else if (application.getRepositoryFolder() != null && !application.getRepositoryFolder().trim().isEmpty()) {
            File file = new File(application.getRepositoryFolder().trim());
            if (!file.exists() || !file.isDirectory()) {
                return applicationDirectory;
            } else {
                return file;
            }
        }

        return applicationDirectory;
    }
	
}
