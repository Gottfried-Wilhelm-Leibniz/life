package filesystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemTest {
    private static final int NUMOFBLOCKS = 10;
    private static final int InodesBLOCKS = 1;
    private static final int BLOCKSIZE = 4000;
    private static final int MAGICNUMBER = 1695609;
    private static final int INODESIZE = 32;
    private static final int INODESTOTAL = InodesBLOCKS * BLOCKSIZE / INODESIZE;
    private static Disc disc;
    private static FileSystem m_fs;
    @BeforeAll
    static void init () throws IOException {
        disc = new Disc(Path.of("./discs"), MAGICNUMBER, NUMOFBLOCKS, InodesBLOCKS, INODESTOTAL, INODESIZE, BLOCKSIZE);
        var buff = ByteBuffer.allocate(BLOCKSIZE);
        buff.position(0);
        buff.putInt(1000);
        buff.putInt(10);
        buff.putInt(1);
        buff.putInt(125);
        buff.putInt(32);
        buff.putInt(4000);
        buff.flip();
        disc.write(0, buff);
        buff.flip();
        buff.putInt(1);
        buff.putInt(1000);
        buff.putInt(3);
        buff.flip();
        disc.write(1, buff);
        buff = ByteBuffer.allocate(BLOCKSIZE);
        buff.putChar('a');
        buff.putChar('b');
        buff.putChar('c');
        buff.putChar('\0');
        buff.putInt(5);
        buff.putChar('d');
        buff.putChar('e');
        buff.putChar('f');
        buff.putChar('\0');
        buff.putInt(4);
        buff.flip();
        disc.write(3, buff);
        m_fs = new FileSystem(disc);
    }

    @Test
    void open() {

    }

    @Test
    void getFilesList() {
        List<String> list = m_fs.getFilesList();
        Assertions.assertTrue(list.size() == 2);
        Assertions.assertEquals("abc", list.get(0));
        Assertions.assertEquals("def", list.get(1));
    }
    @Test
    void formatTest() throws IOException {
        var disc = new Disc(Path.of("./discs"), 900, NUMOFBLOCKS, InodesBLOCKS, INODESTOTAL, INODESIZE, 200);
        var fs = new FileSystem(disc);
        Assertions.assertEquals(1695609, fs.getM_magicBlock().m_magicNum());
        Assertions.assertEquals(4000, fs.getM_magicBlock().m_blockSize());
    }
    @Test
    void refListTest () {
        Map<String,Integer> map = m_fs.getM_filesMap();
        List<String> list = m_fs.getFilesList();
        Assertions.assertEquals(map.get(list.get(0)), 5);
        Assertions.assertEquals(map.get(list.get(1)), 4);
    }
}