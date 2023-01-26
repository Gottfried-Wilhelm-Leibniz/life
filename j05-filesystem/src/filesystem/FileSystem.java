package filesystem;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileSystem {
    private static final int NUMOFBLOCKS = 10;
    private static final int InodesBLOCKS = 1;
    private static final int BLOCKSIZE = 4000;
    private static final int MAGICNUMBER = 1695609;
    private static final int INODESIZE = 32;
    private static final int INODESTOTAL = InodesBLOCKS * BLOCKSIZE / INODESIZE;
    private Disc m_disc;
    private MagicBlock m_magicBlock;
    private ConcurrentHashMap<String, Integer> m_filesMap = new ConcurrentHashMap<>();

    public FileSystem(Disc disc) throws IOException, BufferIsNotTheSizeOfAblockException {
        m_disc = disc;
        var superBuffer =  ByteBuffer.allocate(BLOCKSIZE);
        MagicBlock magicBlock;
        try {
            m_disc.read(0, superBuffer);
            superBuffer.flip();
            magicBlock = new MagicBlock(superBuffer);
        } catch (BufferOverflowException | IllegalArgumentException | BufferIsNotTheSizeOfAblockException e) {
            format();
            superBuffer.rewind();
            m_disc.read(0, superBuffer);
            superBuffer.flip();
            magicBlock = new MagicBlock(superBuffer);
        }
        m_magicBlock = magicBlock;
        initializeFilesMap();
    }

    private void initializeFilesMap() throws IOException, BufferIsNotTheSizeOfAblockException {
            List<Integer> list = getListOfBlocks( 0);
            m_filesMap.clear();
            for (int refBlock : list) {
                addToMapFromBlock(refBlock);
            }
    }

    public File open(String str) throws IOException, BufferIsNotTheSizeOfAblockException {
        for (Map.Entry<String,Integer> entry : m_filesMap.entrySet()){
            if (entry.getKey().equals(str)) {
                return new File(()-> m_disc, str, entry.getValue(), (a)-> {
                    try {
                        return getListOfBlocks(a);
                    } catch (IOException | BufferIsNotTheSizeOfAblockException e) {
                        throw new RuntimeException(e);
                    }
                }, m_magicBlock, (ByteBuffer b, int inode, int size) -> {
                    try {
                        saveToDisc(b, inode, size);
                    } catch (IOException | BufferIsNotTheSizeOfAblockException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        throw new FileNotFoundException("the file you searched is not on the disc");
    }

    private void format() throws IOException {
        m_disc = new Disc(Path.of("./discs"), MAGICNUMBER, NUMOFBLOCKS, InodesBLOCKS , INODESIZE , INODESTOTAL, BLOCKSIZE);
    }

    public List<String> getFilesList() {
        var list = new ArrayList<String>(m_filesMap.size());
        for (Map.Entry<String,Integer> entry : m_filesMap.entrySet()) {
            list.add(entry.getKey());
        }
        return list;
    }

    private List<Integer> getListOfBlocks(int iNodeRef) throws IOException, BufferIsNotTheSizeOfAblockException {
        var inodeBlock = iNodeRef * m_magicBlock.m_inodeSize() / m_magicBlock.m_blockSize() + 1;//(int)Math.ceil((double) iNodeRef * m_magicBlock.m_inlodeBlocks() / m_magicBlock.m_totalInodes());
        var filesInodeBuffer = ByteBuffer.allocate(m_magicBlock.m_blockSize());
        m_disc.read(inodeBlock, filesInodeBuffer);
//        filesInodeBuffer.flip();
        List<Integer> list = new ArrayList<>();
        filesInodeBuffer.position(iNodeRef % (m_magicBlock.m_blockSize()/m_magicBlock.m_inodeSize()) * m_magicBlock.m_inodeSize());
        if (filesInodeBuffer.getInt() == 0) {
            throw new FileNotFoundException();
        }
        int totalSize = filesInodeBuffer.getInt();
        int dataFilesBlocks = (int)Math.ceil((double)totalSize/ m_magicBlock.m_blockSize());
        //int blockRef = filesInodeBuffer.getInt();
        for (int i = 0; i < dataFilesBlocks && i < 5; i++) {
            var blockRef = filesInodeBuffer.getInt();
            list.add(blockRef);
        }

        if (dataFilesBlocks > 5) {
            var indirectRef = filesInodeBuffer.getInt();
            var indirectBlock = ByteBuffer.allocate(m_magicBlock.m_blockSize());
            m_disc.read(indirectRef, indirectBlock);
            indirectBlock.flip();
            for (int i = 5; i < dataFilesBlocks; i++) {
                var blockRef = indirectBlock.getInt();
                list.add(blockRef);
            }
        }
            return list;
    }
    private void addToMapFromBlock(int blockRef) throws IOException, BufferIsNotTheSizeOfAblockException {
        var dataFromBlock = ByteBuffer.allocate(m_magicBlock.m_blockSize());
        m_disc.read(blockRef, dataFromBlock);
        dataFromBlock.flip();
        while (dataFromBlock.position() < m_magicBlock.m_blockSize()) {
            try {
                var strName = new StringBuilder(14);
                char nextChar = dataFromBlock.getChar();
                if(nextChar == (byte) 0) {
                    break;
                }
                while (nextChar != '\0') {
                    strName.append(nextChar);
                    nextChar = dataFromBlock.getChar();
                }
                m_filesMap.put(strName.toString(), dataFromBlock.getInt());
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }
    }
    public void saveToDisc(ByteBuffer buffer, int inode, int size) throws IOException, BufferIsNotTheSizeOfAblockException {
        var inodeBlock = (int)Math.ceil((double) inode * m_magicBlock.m_inlodeBlocks() / m_magicBlock.m_totalInodes());
        var inodeBuffer = ByteBuffer.allocate(m_magicBlock.m_blockSize());
        m_disc.read(inodeBlock, inodeBuffer);
        inodeBuffer.position(m_magicBlock.m_inodeSize() * inode);
        inodeBuffer.putInt(1);
        inodeBuffer.putInt(size);
        int dataFilesBlocks = (int) Math.ceil((double) size / m_magicBlock.m_blockSize());
        var dataSingleBlock = ByteBuffer.allocate(m_magicBlock.m_blockSize());
        for (int i = 0; i < dataFilesBlocks && i < 5; i++) {
            var blockRef = inodeBuffer.getInt();
            if(blockRef == 0) {
                blockRef = getNewBlock();
                inodeBuffer.position(inodeBuffer.position() - 4);
                inodeBuffer.putInt(blockRef);
            }
            dataSingleBlock.rewind();
            dataSingleBlock.put(buffer.array(), i * m_magicBlock.m_blockSize(), m_magicBlock.m_blockSize());
            dataSingleBlock.flip();
            m_disc.write(blockRef, dataSingleBlock);
        }
        if (dataFilesBlocks > 5) {
            var indirectBlock = ByteBuffer.allocate(m_magicBlock.m_blockSize());
            var indirectRef = inodeBuffer.getInt();
            if (indirectRef == 0) {
                indirectRef = getNewBlock();
                inodeBuffer.position(inodeBuffer.position() - 4);
                inodeBuffer.putInt(indirectRef);
            }
            m_disc.read(indirectRef, indirectBlock);
            indirectBlock.rewind();
            for (int i = 5; i < dataFilesBlocks; i++) {
                var blockRef = indirectBlock.getInt();
                if (blockRef == 0) {
                    blockRef = getNewBlock();
                    indirectBlock.position(indirectBlock.position() - 4);
                    indirectBlock.putInt(blockRef);
                }
                dataSingleBlock.rewind();
                dataSingleBlock.put(buffer.array(), i * m_magicBlock.m_blockSize(), m_magicBlock.m_blockSize());
                dataSingleBlock.flip();
                m_disc.write(blockRef, dataSingleBlock);
            }
            indirectBlock.rewind();
            m_disc.write(indirectRef, indirectBlock);
        }
        inodeBuffer.rewind();
        m_disc.write(inodeBlock, inodeBuffer);
        initializeFilesMap();
    }
    public void removeFile(String name) throws IOException, BufferIsNotTheSizeOfAblockException {
        int iNodeDel = m_filesMap.get(name);
        var firstBlocKCopy = ByteBuffer.allocate(m_magicBlock.m_blockSize());
        m_disc.read(1, firstBlocKCopy);
        var temp = ByteBuffer.allocate(m_magicBlock.m_blockSize());

        temp.put(firstBlocKCopy.array(), 0, iNodeDel * m_magicBlock.m_inodeSize());
        temp.put(firstBlocKCopy.array(), iNodeDel * m_magicBlock.m_inodeSize() + m_magicBlock.m_inodeSize(), firstBlocKCopy.array().length - (iNodeDel + 1) * m_magicBlock.m_inodeSize());
        temp.flip();
        m_disc.write(1, temp);
        initializeFilesMap();
    }
    public void createNewFile (String name) throws IOException, BufferIsNotTheSizeOfAblockException {
        var newDataBlock = getNewBlock();
        var inodeAdd = getFilesList().size() + 1;
        var inodeBlock = (int)Math.ceil((double) inodeAdd * m_magicBlock.m_inlodeBlocks() / m_magicBlock.m_totalInodes());
        var firstBlocKCopy = ByteBuffer.allocate(m_magicBlock.m_blockSize());
        m_disc.read(inodeBlock, firstBlocKCopy);
        firstBlocKCopy.position(inodeAdd % m_magicBlock.m_blockSize() * m_magicBlock.m_inodeSize());
        firstBlocKCopy.putInt(1);
        firstBlocKCopy.putInt(m_magicBlock.m_blockSize());
        firstBlocKCopy.putInt(newDataBlock);
        firstBlocKCopy.rewind();
        m_disc.write(inodeBlock, firstBlocKCopy);

        List<Integer> list = getListOfBlocks( 0);
        var filesBlock = list.get(list.size() - 1);
        firstBlocKCopy.rewind();
        m_disc.read(filesBlock, firstBlocKCopy);
        firstBlocKCopy.rewind();
        char nextChar = firstBlocKCopy.getChar();
        while (firstBlocKCopy.position() < m_magicBlock.m_blockSize() && nextChar != (byte) 0) {
            nextChar = firstBlocKCopy.getChar();
        }
        if (m_magicBlock.m_blockSize() - firstBlocKCopy.position() < name.length() * 2 + 4) {
            var numOfFilesData = list.size();
            ///////////////////////////////////////////// never ending
            filesBlock = getNewBlock();
            firstBlocKCopy.rewind();
        }
            for (int i = 0; i < name.length(); i++) {
                firstBlocKCopy.putChar(name.charAt(i));
            }
            firstBlocKCopy.putChar('\0');
            firstBlocKCopy.putInt(inodeAdd);
            firstBlocKCopy.rewind();
            m_disc.write(filesBlock, firstBlocKCopy);

    }
    public int getNewBlock() throws IOException, BufferIsNotTheSizeOfAblockException {
        var list = new ArrayList<Integer>();
        for(Map.Entry<String, Integer> entry : m_filesMap.entrySet()) {
            list.addAll(getListOfBlocks(entry.getValue()));
        }
        for (int i = 2; i < NUMOFBLOCKS; i++) {
            if(!list.contains(i)) {
                return i;
            }
        }
        throw new NoSpaceOnDiscException();
    }
    public MagicBlock getM_magicBlock() {
        return m_magicBlock;
    }
    public ConcurrentHashMap<String, Integer> getM_filesMap() {
        return m_filesMap;
    }
}
