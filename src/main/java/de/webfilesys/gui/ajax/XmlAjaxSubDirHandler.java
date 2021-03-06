package de.webfilesys.gui.ajax;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import de.webfilesys.DirTreeStatus;
import de.webfilesys.SubdirExistCache;
import de.webfilesys.TestSubDirThread;
import de.webfilesys.decoration.Decoration;
import de.webfilesys.decoration.DecorationManager;
import de.webfilesys.graphics.ThumbnailThread;
import de.webfilesys.util.StringComparator;
import de.webfilesys.util.UTF8URLEncoder;

/**
 * @author Frank Hoehnel
 */
public class XmlAjaxSubDirHandler extends XmlRequestHandlerBase
{
	DirTreeStatus dirTreeStatus = null;
	
	public XmlAjaxSubDirHandler(
    		HttpServletRequest req, 
    		HttpServletResponse resp,
            HttpSession session,
            PrintWriter output, 
            String uid)
	{
        super(req, resp, session, output, uid);
	}

	protected void process()
	{
		String expandDir = req.getParameter("path");

		if (!accessAllowed(expandDir))
		{
			Logger.getLogger(getClass()).warn("user " + this.getUid() + " tried to access directory outside the home directory: " + expandDir);
			
			try
			{
				resp.sendError(HttpServletResponse.SC_FORBIDDEN);
			}
			catch (IOException ioex)
			{
				Logger.getLogger(getClass()).warn(ioex);
			}
			
			return;
		}

		String lastInLevel = req.getParameter("lastInLevel");
		
		if ((lastInLevel == null) || (lastInLevel.trim().length() == 0))
		{
             lastInLevel = "false";
		}
		
		dirTreeStatus = (DirTreeStatus) session.getAttribute("dirTreeStatus");
		
		if (dirTreeStatus == null)
		{
			dirTreeStatus = new DirTreeStatus();
			
			session.setAttribute("dirTreeStatus", dirTreeStatus);
		}
		
        dirTreeStatus.expandDir(expandDir);
		
		String actPath = null;
		
		if (File.separatorChar == '/')
		{
			actPath = expandDir;
		}
		else
		{
			if (expandDir.charAt(0) > 'Z')
			{
				char driveChar = (char) ('A' + (expandDir.charAt(0)-'a'));

				actPath = driveChar + expandDir.substring(1);
			}
			else
			{
				actPath = expandDir;
			}
		}

		Element subFolderElement = dirSubTree(actPath, lastInLevel);
		
		doc.appendChild(subFolderElement);
		
		processResponse();
	}
	
	protected Element dirSubTree(String parentPath, String lastInLevel)
	{
		// first the parent dir
		
		Element parentElement = null;

		String encodedPath = UTF8URLEncoder.encode(parentPath);

		boolean hasSubdirs = true;

		Integer subdirExist= SubdirExistCache.getInstance().existsSubdir(parentPath);

		if (subdirExist == null)
		{
	        (new TestSubDirThread(parentPath)).start();
		}
		else
		{
			hasSubdirs = (subdirExist.intValue()==1);
		}

		parentElement = doc.createElement("parentFolder");
        
        if (parentPath.charAt(parentPath.length() - 1) == File.separatorChar)
        {
            parentElement.setAttribute("name", parentPath);
        }
        else
        {
            parentElement.setAttribute("name", parentPath.substring(parentPath.lastIndexOf(File.separatorChar)+1));                
        }
        
		if (File.separatorChar == '\\')
		{
			if (parentPath.length() <= 3)
			{
				char driveChar = parentPath.charAt(0);
				
				if ((driveChar == 'a') || (driveChar == 'A') || (driveChar == 'b') || (driveChar == 'B'))
				{
					parentElement.setAttribute("type", "floppy");
                }
                else
                {
					parentElement.setAttribute("type", "drive");
                }
			}
		}
		
		long folderId = System.currentTimeMillis() - 1;

		parentElement.setAttribute("id", Long.toString(folderId));

  	    parentElement.setAttribute("lastInLevel", lastInLevel);

		parentElement.setAttribute("path", encodedPath);      

		if (!hasSubdirs)
		{
			parentElement.setAttribute("leaf", "true");    
		}

		parentElement.setAttribute("current","true");
		
		DecorationManager decoMgr = DecorationManager.getInstance();

		Decoration deco = decoMgr.getDecoration(parentPath);
		
		if (deco != null) 
		{
			if (deco.getIcon() != null) 
			{
				parentElement.setAttribute("icon", deco.getIcon());
			}
			if (deco.getTextColor() != null)
			{
				parentElement.setAttribute("textColor", deco.getTextColor());
			}
		}
		
		if (File.separatorChar == '/')
		{
		    // there is no way to detect NTFS symbolic links / junctions with Java functions
		    // see http://stackoverflow.com/questions/3249117/cross-platform-way-to-detect-a-symbolic-link-junction-point

		    File linkTestFile = new File(parentPath);

	        if (dirIsLink(linkTestFile))
	        {
	            try
	            {
	                parentElement.setAttribute("link", "true");
	                parentElement.setAttribute("linkDir", linkTestFile.getCanonicalPath());
	            }
	            catch (IOException ioex)
	            {
	                Logger.getLogger(getClass()).error(ioex);
	            }
	        }
		}

		// and now the subdirectories
		
		File subdirFile = new File(parentPath);

		String fileList[] = subdirFile.list();

		if (fileList == null)
		{
			Logger.getLogger(getClass()).warn("filelist is null for " + parentPath);
			
			dirTreeStatus.collapseDir(parentPath);
			
			return(parentElement);
		}

		if (fileList.length == 0)
		{
			return(parentElement);
		}

		String pathWithSlash = parentPath;

		if (!parentPath.endsWith(File.separator))
		{
			pathWithSlash = parentPath + File.separator;
		}

		Vector subdirList = new Vector();

		for (int i = 0; i < fileList.length; i++)
		{
			String subdirPath = pathWithSlash + fileList[i];

			File tempFile = new File(subdirPath);

			if (tempFile.isDirectory())
			{
				String subdirName = fileList[i];

				if (!subdirName.equals(ThumbnailThread.THUMBNAIL_SUBDIR))
				{
					subdirList.add(subdirPath);
				}
			}
		}

		if (subdirList.size() == 0)
		{
			return(parentElement);
		}

		if (subdirList.size() > 1)
		{
			Collections.sort(subdirList, new StringComparator(StringComparator.SORT_IGNORE_CASE));
		}

		for (int i = 0; i < subdirList.size(); i++)
		{
			String subdirPath=(String) subdirList.elementAt(i);

			Element folderElement = null;

			hasSubdirs = true;

			subdirExist = SubdirExistCache.getInstance().existsSubdir(subdirPath);

			if (subdirExist == null)
			{
		        (new TestSubDirThread(subdirPath)).start();
			}
			else
			{
				hasSubdirs = (subdirExist.intValue()==1);
			}

            folderElement = doc.createElement("folder");
            
            parentElement.appendChild(folderElement);
            
            String folderName = subdirPath.substring(subdirPath.lastIndexOf(File.separatorChar) + 1);

            folderElement.setAttribute("name", folderName);                

			folderId = System.currentTimeMillis() + i;

			folderElement.setAttribute("id", Long.toString(folderId));

            if (subdirPath.indexOf('\'') > 0) {
                encodedPath = UTF8URLEncoder.encode(subdirPath.replace('\'', '`'));
            } else {
                encodedPath = UTF8URLEncoder.encode(subdirPath);
            }

			folderElement.setAttribute("path", encodedPath);      

            if (!hasSubdirs)
			{
				folderElement.setAttribute("leaf", "true");    
			}
			
			if (i == subdirList.size() - 1)
			{
				folderElement.setAttribute("lastInLevel", "true");
			}
			else
			{
				folderElement.setAttribute("lastInLevel" , "false");
			}
			
			deco = decoMgr.getDecoration(subdirPath);
			
			if (deco != null) 
			{
				if (deco.getIcon() != null) 
				{
	                folderElement.setAttribute("icon", deco.getIcon());
				}
				if (deco.getTextColor() != null) 
				{
	                folderElement.setAttribute("textColor", deco.getTextColor());
				}
			}

			if (File.separatorChar == '/')
			{
				File tempFile = new File(subdirPath);

				if (dirIsLink(tempFile))
				{
					try
					{
						folderElement.setAttribute("link", "true");
                    
						folderElement.setAttribute("linkDir", tempFile.getCanonicalPath());
					}
					catch (IOException ioex)
					{
						Logger.getLogger(getClass()).error(ioex);
					}
				}
			}
		}
		
		return(parentElement);
	}
	
}