/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.coreedge.core.state.storage;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.neo4j.coreedge.identity.MemberId;
import org.neo4j.coreedge.messaging.EndOfStreamException;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.log.FlushableChannel;
import org.neo4j.kernel.impl.transaction.log.PhysicalFlushableChannel;
import org.neo4j.kernel.impl.transaction.log.ReadAheadChannel;
import org.neo4j.kernel.impl.transaction.log.ReadableClosableChannel;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;

public class MemberIdStorage
{
    private final FileSystemAbstraction fileSystem;
    private final MemberId.MemberIdMarshal marshal;
    private final File file;
    private Log log;

    public MemberIdStorage( FileSystemAbstraction fileSystem, File directory, String name,
            MemberId.MemberIdMarshal marshal, LogProvider logProvider )
    {
        this.fileSystem = fileSystem;
        this.log = logProvider.getLog( getClass() );
        this.file = new File( DurableStateStorage.stateDir( directory, name ), name );
        this.marshal = marshal;
    }

    public boolean exists()
    {
        return fileSystem.fileExists( file );
    }

    public MemberId readState() throws IOException
    {
        if ( exists() )
        {
            try ( ReadableClosableChannel channel = new ReadAheadChannel<>( fileSystem.open( file, "r" ) ) )
            {
                MemberId memberId = marshal.unmarshal( channel );
                if ( memberId != null )
                {
                    return memberId;
                }
            }
            catch ( EndOfStreamException e )
            {
                log.error( "End of stream reached" );
            }
        }
        else
        {
            log.warn( "File does not exist" );
        }

        fileSystem.mkdirs( file.getParentFile() );
        fileSystem.deleteFile( file );

        UUID uuid = UUID.randomUUID();
        MemberId memberId = new MemberId( uuid );
        log.info( String.format( "Generated new id: %s (%s)", memberId, uuid ) );
        try ( FlushableChannel channel = new PhysicalFlushableChannel( fileSystem.create( file ) ) )
        {
            marshal.marshal( memberId, channel );
        }
        return memberId;
    }
}