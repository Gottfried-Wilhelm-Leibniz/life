package filesystem.Tests;

import filesystem.Disc;
import filesystem.FileSystem;
import filesystem.SuperBlock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

class SuperBlockTest {
    private static final int NUMOFBLOCKS = 10;
    private static final int InodesBLOCKS = 1;
    private static final int BLOCKSIZE = 4000;
    private static final int MAGICNUMBER = 1695609;
    private static final int INODESIZE = 32;
    private static final int INODESTOTAL = InodesBLOCKS * BLOCKSIZE / INODESIZE;
    private static Disc disc;
    private static FileSystem fs;
    @Test
    void magicBlockTest() throws IOException {
        disc = new Disc(Path.of("./discs"), NUMOFBLOCKS, BLOCKSIZE);
        var buff = ByteBuffer.allocate(BLOCKSIZE);
        buff.position(0);
        buff.putInt(1000);
        buff.putInt(10);
        buff.putInt(1);
        buff.putInt(125);
        buff.putInt(32);
        buff.putInt(4000);
        System.out.println(buff.position());
        buff.flip();
        disc.write(0, buff);
        buff.flip();
        var magicBlock = new SuperBlock(buff);
        Assertions.assertEquals(1000, magicBlock.magic());
        Assertions.assertEquals(10, magicBlock.numBlocks());
        Assertions.assertEquals(1, magicBlock.inodeBlocks());
        Assertions.assertEquals(125, magicBlock.totalInodes());
        Assertions.assertEquals(32, magicBlock.inodeSize());
        Assertions.assertEquals(4000, magicBlock.blockSize());
    }

}